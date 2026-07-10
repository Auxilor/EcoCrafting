package io.auxilor.ecocrafting.crafting.integration

import com.willfp.eco.core.recipe.workstation.CrafterRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import io.auxilor.ecocrafting.EcoCraftingPlugin
import io.auxilor.ecocrafting.crafting.service.BlockOwnerService
import io.auxilor.ecocrafting.crafting.service.checkCraftingConditions
import io.auxilor.ecocrafting.crafting.service.fireCraftEffects
import io.auxilor.ecocrafting.recipe.service.RecipeService
import io.auxilor.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.NamespacedKey
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
        val baseKey = if (recipeKey.namespace == "ecocrafting" && recipeKey.key.endsWith("_crafter"))
            NamespacedKey("ecocrafting", recipeKey.key.removeSuffix("_crafter"))
        else recipeKey

        val recipe = WorkstationRecipes.getByKey(baseKey) as? CrafterRecipe ?: return
        val meta = recipeService.getMeta(baseKey) ?: return

        // Opt-in: only handle Crafter for recipes that declare support-crafter=true.
        // Without this gate, eco's AutocrafterPatch cancels the event and we'd be
        // silently uncancelling every EcoCrafting recipe.
        if (!meta.supportCrafter) return

        if (!meta.giveResultItem) {
            event.isCancelled = true
            val crafterInventory = (event.block.state as? Crafter)?.inventory ?: return
            for (slot in 0 until 9) consume(crafterInventory, slot)
            val player = blockOwnerService.getOwner(event.block.location) ?: return
            if (!checkCraftingConditions(plugin, unlockService, player, recipe, meta)) return
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
        event.isCancelled = false
        event.result = item
        fireCraftEffects(player, recipe, meta, item, 1, event.block)
    }
}
