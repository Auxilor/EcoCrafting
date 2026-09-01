package com.exanthiax.ecocrafting.crafting.integration

import com.willfp.eco.core.recipe.workstation.CrafterRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.crafting.service.BlockOwnerService
import com.exanthiax.ecocrafting.crafting.service.checkCraftingConditions
import com.exanthiax.ecocrafting.crafting.service.fireCraftEffects
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.block.Crafter
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.CrafterCraftEvent

class CrafterBlockListener(
    private val plugin: EcoCraftingPlugin,
    private val recipeService: RecipeService,
    private val unlockService: RecipeUnlockService,
    private val blockOwnerService: BlockOwnerService
) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onCrafterCraft(event: CrafterCraftEvent) {
        val recipeKey = (event.recipe as? org.bukkit.Keyed)?.key ?: return
        // Normalize the fired key (`_crafter` suffix and/or a symmetry-variant `<id>_rot90`)
        // to the base recipe, so a rotated placement in a crafter block still resolves and
        // gets its lock/price/condition checks.
        val baseKey = recipeService.resolveBaseKey(recipeKey)

        val recipe = WorkstationRecipes.getByKey(baseKey) as? CrafterRecipe ?: return
        val meta = recipeService.getMeta(baseKey) ?: return

        // Opt-in: only handle Crafter for recipes that declare support-crafter=true.
        // Without this gate, eco's AutocrafterPatch cancels the event and we'd be
        // silently uncancelling every EcoCrafting recipe.
        if (!meta.supportCrafter) return

        val crafterInventory = (event.block.state as? Crafter)?.inventory
        // The `_crafter` Bukkit recipe uses ExactChoice, which ignores stack size, so the
        // crafter fires with a single item in a slot a `<item> 4` part asked four of.
        val requirements = crafterInventory
            ?.let { gridRequirements(recipe, it.contents) }
            ?: emptyMap()
        if (crafterInventory != null && !gridSatisfies(crafterInventory.contents, requirements)) {
            event.isCancelled = true
            return
        }

        if (!meta.giveResultItem) {
            event.isCancelled = true
            crafterInventory ?: return
            val player = blockOwnerService.getOwner(event.block.location) ?: return
            if (!checkCraftingConditions(plugin, unlockService, player, recipe, meta)) return
            for (slot in 0 until 9) consume(crafterInventory, slot, requirements[slot] ?: 1)
            meta.price.pay(player, 1.0)
            val item = recipe.output?.clone() ?: return
            fireCraftEffects(player, recipe, meta, item, 1, event.block)
            return
        }
        // giveResultItem = true: eco's AutocrafterPatch cancels every eco-namespace
        // CrafterCraftEvent. Uncancel and set result so vanilla delivers + consumes.
        val item = recipe.output?.clone() ?: return
        val player = blockOwnerService.getOwner(event.block.location) ?: return
        if (!checkCraftingConditions(plugin, unlockService, player, recipe, meta)) { event.isCancelled = true; return }
        meta.price.pay(player, 1.0)
        // Vanilla removes exactly one item per slot after this event, so anything a part
        // needs beyond that first item has to come out of the grid here.
        if (crafterInventory != null) {
            for (slot in 0 until 9) {
                val extra = (requirements[slot] ?: 1) - 1
                if (extra > 0) consume(crafterInventory, slot, extra)
            }
        }
        event.isCancelled = false
        event.result = item
        fireCraftEffects(player, recipe, meta, item, 1, event.block)
    }
}
