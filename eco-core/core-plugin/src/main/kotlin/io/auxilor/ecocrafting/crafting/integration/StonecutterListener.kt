package io.auxilor.ecocrafting.crafting.integration

import com.willfp.eco.core.recipe.workstation.StonecuttingRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import io.auxilor.ecocrafting.EcoCraftingPlugin
import io.auxilor.ecocrafting.crafting.event.CustomCraftEvent
import io.auxilor.ecocrafting.crafting.service.checkCraftingConditions
import io.auxilor.ecocrafting.crafting.service.fireCraftEffects
import io.auxilor.ecocrafting.crafting.service.priceAffordableAmount
import io.auxilor.ecocrafting.recipe.service.RecipeService
import io.auxilor.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.StonecutterInventory
import org.bukkit.inventory.Inventory

class StonecutterListener(
    private val plugin: EcoCraftingPlugin,
    private val recipeService: RecipeService,
    private val unlockService: RecipeUnlockService
) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        if (event.view.topInventory !is StonecutterInventory) return

        val player = event.whoClicked as? Player ?: return
        val recipeKey = (event.recipe as? org.bukkit.Keyed)?.key ?: return

        val recipe = WorkstationRecipes.getByKey(recipeKey) as? StonecuttingRecipe
            ?: run {
                plugin.debug("[Stonecutter] no recipe for key=$recipeKey")
                return
            }

        val meta = recipeService.getMeta(recipeKey) ?: return

        plugin.debug("[Stonecutter] onCraft: key=$recipeKey giveResultItem=${meta.giveResultItem}")

        if (!checkCraftingConditions(plugin, unlockService, player, recipe, meta)) { event.isCancelled = true; return }

        val amount = priceAffordableAmount(player, meta.price, calculateCraftAmount(event, maxCraftsFromInput(event.view.topInventory.getItem(0)))).coerceAtLeast(1)
        val item = recipe.output?.clone()?.apply { this.amount = amount } ?: return
        val customEvent = CustomCraftEvent(player, recipe, item, amount)
        Bukkit.getPluginManager().callEvent(customEvent)
        if (customEvent.isCancelled) { event.isCancelled = true; return }

        meta.price.pay(player, amount.toDouble())
        if (!meta.giveResultItem) {
            event.isCancelled = true
            consumeStonecutterSlot(event.view.topInventory, amount)
            plugin.server.scheduler.runTask(plugin, Runnable { player.updateInventory() })
        }
        fireCraftEffects(player, recipe, meta, item, amount, event.view.topInventory.location?.block)
        plugin.debug("[Stonecutter] effects fired for recipe=${recipe.key}")
    }

    // Stonecutter effect-recipe result-click
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onStonecutterResultClick(event: InventoryClickEvent) {
        if (event.inventory.type != InventoryType.STONECUTTER) return
        if (event.rawSlot != 1) return
        val player = event.whoClicked as? Player ?: return

        val inventory = event.inventory
        val inputItem = inventory.getItem(0) ?: return
        val resultItem = inventory.getItem(1) ?: return

        val recipe = WorkstationRecipes.getAll(StonecuttingRecipe::class.java)
            .firstOrNull { it.input.matches(inputItem) && it.output?.isSimilar(resultItem) == true } ?: return
        val meta = recipeService.getMeta(recipe.key) ?: return
        if (meta.giveResultItem) return

        plugin.debug("[Stonecutter] onStonecutterResultClick: no-item recipe=${recipe.key}")
        if (!checkCraftingConditions(plugin, unlockService, player, recipe, meta)) { event.isCancelled = true; return }

        val item = resultItem.clone()
        val amount = if (event.isShiftClick) {
            priceAffordableAmount(player, meta.price, minOf(spaceBasedAmount(player, item), maxCraftsFromInput(inputItem)).coerceAtLeast(1))
        } else priceAffordableAmount(player, meta.price, 1)

        val craftItem = item.clone().apply { this.amount = amount }
        event.isCancelled = true
        val customEvent = CustomCraftEvent(player, recipe, craftItem, amount)
        Bukkit.getPluginManager().callEvent(customEvent)
        if (customEvent.isCancelled) {
            plugin.server.scheduler.runTask(plugin, Runnable { player.updateInventory() })
            return
        }
        consumeStonecutterSlot(inventory, amount)
        meta.price.pay(player, amount.toDouble())
        fireCraftEffects(player, recipe, meta, craftItem, amount, inventory.location?.block)
        plugin.server.scheduler.runTask(plugin, Runnable { player.updateInventory() })
    }

    private fun consumeStonecutterSlot(inventory: Inventory, amount: Int) {
        consume(inventory, 0, amount)
    }

    private fun calculateCraftAmount(event: CraftItemEvent, ingredientBasedAmount: Int): Int {
        return if (event.isShiftClick) {
            val result = event.recipe.result
            val player = event.whoClicked as Player
            val spaceBased = spaceBasedAmount(player, result)
            val amount = minOf(spaceBased, ingredientBasedAmount).coerceAtLeast(1)
            if (ingredientBasedAmount < spaceBased) {
                plugin.debug("[CraftAmount] capped by ingredients: space=$spaceBased ingredients=$ingredientBasedAmount -> $amount")
            }
            amount
        } else 1
    }
}
