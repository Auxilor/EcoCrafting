package io.auxilor.ecocrafting.crafting.integration

import com.willfp.eco.core.recipe.workstation.BrewingRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import io.auxilor.ecocrafting.EcoCraftingPlugin
import io.auxilor.ecocrafting.crafting.service.BlockOwnerService
import io.auxilor.ecocrafting.crafting.service.checkCraftingConditions
import io.auxilor.ecocrafting.crafting.service.fireCraftEffects
import io.auxilor.ecocrafting.recipe.service.RecipeService
import io.auxilor.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.Location
import org.bukkit.block.BrewingStand
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.BrewEvent

class BrewingListener(
    private val plugin: EcoCraftingPlugin,
    private val recipeService: RecipeService,
    private val unlockService: RecipeUnlockService,
    private val blockOwnerService: BlockOwnerService
) : Listener {

    init {
        WorkstationRecipes.registerBrewCompletedHook { location, recipe, matchedSlots ->
            handleBrewCompleted(location, recipe, matchedSlots)
        }
    }

    // Eco's BrewingPacketHandler intercepts ingredient placement and fires the brew
    // directly (no BrewEvent). Logic is handled via WorkstationRecipes.brewCompletedHook
    // registered in init. This handler only cancels stale timers if BrewEvent fires anyway.
    @EventHandler(priority = EventPriority.LOWEST)
    fun onBrew(event: BrewEvent) {
        WorkstationRecipes.cancelPendingBrew(event.block.location)

        // The brewing stand is still a real vanilla block. If base+ingredient also
        // happen to match a genuine vanilla recipe (e.g. potion + glowstone dust),
        // vanilla brews it on its own timer regardless of our custom recipe - handing
        // the player a real vanilla potion and completely bypassing give-result-item:
        // false / our effects. Cancel vanilla's own completion whenever a custom
        // recipe claims this combo; BrewingPacketHandler's own timer (which fires
        // handleBrewCompleted) is the source of truth for these slots.
        val ingredient = event.contents.ingredient ?: return
        val matchesCustomRecipe = WorkstationRecipes.getAll(BrewingRecipe::class.java).any { recipe ->
            recipe.ingredient.matches(ingredient) &&
                (0..2).any { slot -> recipe.base.matches(event.contents.getItem(slot)) }
        }
        if (matchesCustomRecipe) {
            event.isCancelled = true
        }
    }

    private fun handleBrewCompleted(location: Location, recipe: BrewingRecipe, matchedSlots: List<Int>) {
        val meta = recipeService.getMeta(recipe.key) ?: return
        val brewer = (location.block.state as? BrewingStand)?.inventory ?: return
        val player = blockOwnerService.getOwner(location) ?: return

        if (!checkCraftingConditions(plugin, unlockService, player, recipe, meta)) return

        if (!meta.giveResultItem) {
            matchedSlots.forEach { brewer.setItem(it, null) }
        }

        meta.price.pay(player, matchedSlots.size.toDouble())

        val item = recipe.output?.clone() ?: return

        val effectRecipePerSlot = plugin.configYml.getBool("brewing-stand.effect-recipes-per-slot")
        if (!meta.giveResultItem && effectRecipePerSlot) {
            matchedSlots.forEach { _ -> fireCraftEffects(player, recipe, meta, item.clone(), 1, location.block) }
        } else {
            fireCraftEffects(player, recipe, meta, item, matchedSlots.size, location.block)
        }
    }
}
