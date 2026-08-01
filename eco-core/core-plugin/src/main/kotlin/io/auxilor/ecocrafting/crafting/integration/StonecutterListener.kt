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
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory

class StonecutterListener(
    private val plugin: EcoCraftingPlugin,
    private val recipeService: RecipeService,
    private val unlockService: RecipeUnlockService
) : Listener {

    // Bukkit's CraftItemEvent only ever fires for a CraftingInventory (crafting table);
    // taking the result out of a stonecutter is a plain InventoryClickEvent on rawSlot 1,
    // so that's the only place we can gate/charge/effect a stonecutter craft.
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

        event.isCancelled = true

        if (!checkCraftingConditions(plugin, unlockService, player, recipe, meta)) { return }

        val item = resultItem.clone()
        val amount = if (event.isShiftClick) {
            priceAffordableAmount(player, meta.price, minOf(spaceBasedAmount(player, item), maxCraftsFromInput(inputItem)).coerceAtLeast(1))
        } else priceAffordableAmount(player, meta.price, 1)

        val craftItem = item.clone().apply { this.amount = item.amount * amount }
        val customEvent = CustomCraftEvent(player, recipe, craftItem, amount)
        Bukkit.getPluginManager().callEvent(customEvent)
        if (customEvent.isCancelled) {
            plugin.server.scheduler.runTask(plugin, Runnable { player.updateInventory() })
            return
        }
        consumeStonecutterSlot(inventory, amount)
        meta.price.pay(player, amount.toDouble())
        if (meta.giveResultItem) {
            giveOrDropItem(player, craftItem, preferCursor = !event.isShiftClick)
        }
        fireCraftEffects(player, recipe, meta, craftItem, amount, inventory.location?.block)
        plugin.server.scheduler.runTask(plugin, Runnable { player.updateInventory() })
    }

    private fun consumeStonecutterSlot(inventory: Inventory, amount: Int) {
        consume(inventory, 0, amount)
    }

}
