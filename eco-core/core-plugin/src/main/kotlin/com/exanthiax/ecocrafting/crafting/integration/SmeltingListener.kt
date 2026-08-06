package com.exanthiax.ecocrafting.crafting.integration

import com.willfp.eco.core.recipe.workstation.SmeltingRecipe
import com.willfp.eco.core.recipe.workstation.SmeltingType
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.crafting.event.CustomSmeltEvent
import com.exanthiax.ecocrafting.crafting.service.BlockOwnerService
import com.exanthiax.ecocrafting.crafting.service.checkCraftingConditions
import com.exanthiax.ecocrafting.crafting.service.fireCraftEffects
import com.exanthiax.ecocrafting.recipe.model.requiredAmount
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Campfire
import org.bukkit.block.Furnace
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockCookEvent
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.inventory.ItemStack

// Furnace / blast furnace / smoker (FurnaceSmeltEvent) + campfire (BlockCookEvent).
class SmeltingListener(
    private val plugin: EcoCraftingPlugin,
    private val recipeService: RecipeService,
    private val unlockService: RecipeUnlockService,
    private val blockOwnerService: BlockOwnerService
) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSmelt(event: FurnaceSmeltEvent) {
        val location = event.block.location
        val player = blockOwnerService.getActor(location)

        if (player == null) {
            // No player to unlock/price-check against - deny any tracked recipe outright,
            // regardless of give-result-item, rather than letting it complete unchecked.
            val noItemMatch = WorkstationRecipes.getAll(SmeltingRecipe::class.java)
                .firstOrNull { recipe ->
                    recipe.smeltingType != SmeltingType.CAMPFIRE && recipe.input.matches(event.source) &&
                    recipeService.getMeta(recipe.key) != null
                }
            if (noItemMatch != null) event.isCancelled = true
            return
        }

        val recipe = WorkstationRecipes.getAll(SmeltingRecipe::class.java)
            .firstOrNull { it.smeltingType != SmeltingType.CAMPFIRE && it.input.matches(event.source) }
            ?: return
        val meta = recipeService.getMeta(recipe.key) ?: return

        if (!checkCraftingConditions(plugin, unlockService, player, recipe, meta)) { event.isCancelled = true; return }

        val item = recipe.output?.clone() ?: return
        val customEvent = CustomSmeltEvent(player, recipe, item, location)
        Bukkit.getPluginManager().callEvent(customEvent)
        if (customEvent.isCancelled) { event.isCancelled = true; return }

        // Vanilla always consumes exactly 1 source item per smelt and ignores recipe.input's
        // configured amount. Rather than cancelling the event (which would also skip vanilla's
        // burn-time/cook-time bookkeeping), bypass its output via AIR and write the input/output
        // slots ourselves - cook time itself stays vanilla-driven via the registered recipe.
        event.result = ItemStack(Material.AIR)
        val furnaceState = event.block.state as? Furnace ?: return
        val inventory = furnaceState.inventory

        if (meta.giveResultItem) {
            val existingResult = inventory.result
            inventory.result = if (existingResult == null || existingResult.type.isAir) item
                                else existingResult.apply { amount += item.amount }
        }
        inventory.smelting?.let { source ->
            val remaining = source.amount - recipe.input.requiredAmount()
            inventory.smelting = if (remaining <= 0) null else source.apply { amount = remaining }
        }

        meta.price.pay(player, 1.0)
        fireCraftEffects(player, recipe, meta, item, 1, event.block)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCampfire(event: BlockCookEvent) {
        val location = event.block.location
        val player = blockOwnerService.getActor(location)

        if (player == null) {
            // No player to unlock/price-check against - deny any tracked recipe outright,
            // regardless of give-result-item, rather than letting it complete unchecked.
            val noItemMatch = WorkstationRecipes.getAll(SmeltingRecipe::class.java)
                .firstOrNull { recipe ->
                    recipe.smeltingType == SmeltingType.CAMPFIRE && recipe.input.matches(event.source) &&
                    recipeService.getMeta(recipe.key) != null
                }
            if (noItemMatch != null) event.isCancelled = true
            return
        }

        val recipe = WorkstationRecipes.getAll(SmeltingRecipe::class.java)
            .firstOrNull { it.smeltingType == SmeltingType.CAMPFIRE && it.input.matches(event.source) }
            ?: return
        val meta = recipeService.getMeta(recipe.key) ?: return

        if (!checkCraftingConditions(plugin, unlockService, player, recipe, meta)) { event.isCancelled = true; return }

        val item = recipe.output?.clone() ?: return
        val customEvent = CustomSmeltEvent(player, recipe, item, location)
        Bukkit.getPluginManager().callEvent(customEvent)
        if (customEvent.isCancelled) { event.isCancelled = true; return }

        if (!meta.giveResultItem) {
            event.isCancelled = true
            val campfire = event.block.state as? Campfire
            if (campfire != null) {
                val requiredAmount = recipe.input.requiredAmount()
                for (slot in 0 until 4) {
                    val slotItem = campfire.getItem(slot) ?: continue
                    if (slotItem.isSimilar(event.source)) {
                        val remaining = slotItem.amount - requiredAmount
                        if (remaining <= 0) campfire.setItem(slot, null)
                        else { slotItem.amount = remaining; campfire.setItem(slot, slotItem) }
                        campfire.update()
                        break
                    }
                }
            }
        }
        meta.price.pay(player, 1.0)
        fireCraftEffects(player, recipe, meta, item, 1, event.block)
    }
}
