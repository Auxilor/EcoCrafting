package com.exanthiax.ecocrafting.crafting.integration

import com.willfp.eco.core.recipe.workstation.AnvilRecipe
import com.willfp.eco.core.recipe.workstation.GrindstoneRecipe
import com.willfp.eco.core.recipe.workstation.VillagerRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.willfp.eco.util.formatEco
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.crafting.event.CustomWorkbenchCraftEvent
import com.exanthiax.ecocrafting.crafting.service.checkCraftingConditions
import com.exanthiax.ecocrafting.crafting.service.fireCraftEffects
import com.exanthiax.ecocrafting.crafting.service.priceAffordableAmount
import com.exanthiax.ecocrafting.recipe.model.requiredAmount
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.event.inventory.PrepareGrindstoneEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.MerchantInventory

// Grindstone, anvil, and villager trades - all three deliver their result via a plain
// InventoryClickEvent on the output slot rather than a dedicated craft event.
class WorkbenchListener(
    private val plugin: EcoCraftingPlugin,
    private val recipeService: RecipeService,
    private val unlockService: RecipeUnlockService
) : Listener {

    // Grindstone + Anvil prepare (override eco's HIGH firstOrNull)
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPrepareGrindstone(event: PrepareGrindstoneEvent) {
        val inventory = event.inventory
        val recipe = WorkstationRecipes.getAll(GrindstoneRecipe::class.java)
            .filter {
                it.item1.matches(inventory.getItem(0)) &&
                (it.item2 == null || it.item2!!.matches(inventory.getItem(1)))
            }
            // Prefer more specific (two-item) recipes over one-item recipes
            .maxByOrNull { if (it.item2 != null) 1 else 0 }
            ?: return
        recipeService.getMeta(recipe.key) ?: return
        event.result = recipe.output?.clone()
        plugin.server.scheduler.runTask(plugin, Runnable {
            (event.view.player as? Player)?.updateInventory()
        })
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPrepareAnvil(event: PrepareAnvilEvent) {
        val inventory = event.inventory
        val recipe = WorkstationRecipes.getAll(AnvilRecipe::class.java)
            .firstOrNull {
                it.base.matches(inventory.getItem(0)) &&
                (it.material == null || it.material!!.matches(inventory.getItem(1)))
            } ?: return
        recipeService.getMeta(recipe.key) ?: return
        val result = recipe.output?.clone() ?: return
        recipe.resultName?.let { name ->
            val meta = result.itemMeta
            meta?.setDisplayName(name.formatEco())
            result.itemMeta = meta
        }
        event.result = result
        event.inventory.repairCost = recipe.repairCost
        event.inventory.repairCostAmount = recipe.material?.requiredAmount() ?: 1
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inventory = event.inventory

        if (event.rawSlot != 2) return

        val workstationRecipe = when (inventory.type) {
            InventoryType.ANVIL ->
                WorkstationRecipes.getAll(AnvilRecipe::class.java)
                    .firstOrNull {
                        it.base.matches(inventory.getItem(0)) &&
                        (it.material == null || it.material!!.matches(inventory.getItem(1)))
                    } ?: return

            InventoryType.GRINDSTONE ->
                WorkstationRecipes.getAll(GrindstoneRecipe::class.java)
                    .filter {
                        it.item1.matches(inventory.getItem(0)) &&
                        (it.item2 == null || it.item2!!.matches(inventory.getItem(1)))
                    }
                    .maxByOrNull { if (it.item2 != null) 1 else 0 }
                    ?: return

            InventoryType.MERCHANT -> {
                val merchant = inventory as? MerchantInventory ?: return
                val selected = merchant.selectedRecipe ?: return
                WorkstationRecipes.getAll(VillagerRecipe::class.java)
                    .firstOrNull { it.matchesMerchantRecipe(selected) }
                    ?: return
            }

            else -> return
        }

        val meta = recipeService.getMeta(workstationRecipe.key) ?: return
        if (!checkCraftingConditions(plugin, unlockService, player, workstationRecipe, meta)) { event.isCancelled = true; return }

        val output = workstationRecipe.output ?: return

        val amount = if (workstationRecipe is GrindstoneRecipe && event.isShiftClick) {
            val item1Amount = workstationRecipe.item1.requiredAmount()
            val availableFromItem1 = (inventory.getItem(0)?.amount ?: 0) / item1Amount
            val availableFromItem2 = workstationRecipe.item2?.let { item2 ->
                (inventory.getItem(1)?.amount ?: 0) / item2.requiredAmount()
            }
            val ingredientBased = listOfNotNull(availableFromItem1, availableFromItem2).min()
            priceAffordableAmount(player, meta.price, minOf(spaceBasedAmount(player, output), ingredientBased).coerceAtLeast(1))
        } else priceAffordableAmount(player, meta.price, 1)

        val item = output.clone().apply { this.amount = output.amount * amount }
        val stationType = meta.displayType
        val customEvent = CustomWorkbenchCraftEvent(player, workstationRecipe, item, stationType)

        Bukkit.getPluginManager().callEvent(customEvent)
        if (customEvent.isCancelled) { event.isCancelled = true; return }

        when (workstationRecipe) {
            is GrindstoneRecipe, is AnvilRecipe -> {
                event.isCancelled = true
                consumeWorkbenchInputs(inventory, workstationRecipe, amount)
                meta.price.pay(player, amount.toDouble())
                if (meta.giveResultItem) {
                    val preferCursor = workstationRecipe is AnvilRecipe && !event.isShiftClick
                    giveOrDropItem(player, item.clone(), preferCursor)
                }
            }
            is VillagerRecipe -> {
                // Eco already registers the real MerchantRecipe, so leave the click un-cancelled
                // and let vanilla own consume/uses/xp/give - cancelling would double-credit the
                // trade (uses/xp still advance, forcing a second click). Only charge the extra
                // price here; claw back the item next tick if it shouldn't have been given.
                meta.price.pay(player, amount.toDouble())
                if (!meta.giveResultItem) {
                    val taken = item.clone()
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        if (player.itemOnCursor.isSimilar(taken)) {
                            player.setItemOnCursor(null)
                        } else {
                            player.inventory.removeItem(taken)
                        }
                    })
                }
            }
            else -> {
                meta.price.pay(player, amount.toDouble())
            }
        }
        fireCraftEffects(player, workstationRecipe, meta, item, amount, inventory.location?.block)
        WorkstationRecipes.clearPendingRecipe(player.uniqueId)
        // Synchronous safety-net resync, not scheduled - queuing a Runnable per click backs up
        // the scheduler under a rapid/shift-click burst. Only when we cancelled and hand-rolled
        // the movement: an un-cancelled villager trade hasn't had vanilla's transfer happen yet
        // here, so resyncing would show the pre-trade state and force a second click.
        if (event.isCancelled) {
            player.updateInventory()
        }
    }

    private fun VillagerRecipe.matchesMerchantRecipe(merchantRecipe: org.bukkit.inventory.MerchantRecipe): Boolean {
        val ingredients = merchantRecipe.ingredients
        if (ingredients.isEmpty() || !input1.matches(ingredients[0])) return false
        val secondInput = input2
        return if (secondInput != null) ingredients.size > 1 && secondInput.matches(ingredients[1])
               else ingredients.size <= 1
    }

    private fun consumeWorkbenchInputs(inventory: Inventory, recipe: WorkstationRecipe, crafts: Int = 1) {
        when (recipe) {
            is GrindstoneRecipe -> {
                consume(inventory, 0, recipe.item1.requiredAmount() * crafts)
                recipe.item2?.let { consume(inventory, 1, it.requiredAmount() * crafts) }
            }
            is AnvilRecipe -> {
                consume(inventory, 0, recipe.base.requiredAmount() * crafts)
                recipe.material?.let { consume(inventory, 1, it.requiredAmount() * crafts) }
            }
            else -> {}
        }
    }
}
