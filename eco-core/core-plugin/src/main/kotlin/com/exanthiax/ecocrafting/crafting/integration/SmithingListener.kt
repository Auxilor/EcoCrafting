package com.exanthiax.ecocrafting.crafting.integration

import com.willfp.eco.core.recipe.workstation.SmithingRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.crafting.event.CustomSmithEvent
import com.exanthiax.ecocrafting.crafting.service.checkCraftingConditions
import com.exanthiax.ecocrafting.crafting.service.fireCraftEffects
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory

class SmithingListener(
    private val plugin: EcoCraftingPlugin,
    private val recipeService: RecipeService,
    private val unlockService: RecipeUnlockService
) : Listener {

    // Like the stonecutter, CraftItemEvent on a SmithingInventory is informational only -
    // cancelling it doesn't stop the take, since the actual transfer already happened via
    // this InventoryClickEvent on rawSlot 3. That's the only place we can gate/charge/effect.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSmithingResultClick(event: InventoryClickEvent) {
        if (event.inventory.type != InventoryType.SMITHING) return
        if (event.rawSlot != 3) return
        val player = event.whoClicked as? Player ?: return

        val inventory = event.inventory
        val recipe = WorkstationRecipes.getAll(SmithingRecipe::class.java)
            .firstOrNull {
                it.template.matches(inventory.getItem(0)) &&
                it.base.matches(inventory.getItem(1)) &&
                it.addition.matches(inventory.getItem(2))
            } ?: return
        val meta = recipeService.getMeta(recipe.key) ?: return

        event.isCancelled = true

        if (!checkCraftingConditions(plugin, unlockService, player, recipe, meta)) { return }

        val item = recipe.output?.clone() ?: return
        val customEvent = CustomSmithEvent(player, recipe, item)
        Bukkit.getPluginManager().callEvent(customEvent)
        if (customEvent.isCancelled) {
            plugin.server.scheduler.runTask(plugin, Runnable { player.updateInventory() })
            return
        }
        consumeSmithingSlots(inventory)
        meta.price.pay(player, 1.0)
        if (meta.giveResultItem) {
            giveOrDropItem(player, item.clone())
        }
        fireCraftEffects(player, recipe, meta, item, 1, inventory.location?.block)
        plugin.server.scheduler.runTask(plugin, Runnable { player.updateInventory() })
    }

    private fun consumeSmithingSlots(inventory: Inventory) {
        for (slot in 0..2) consume(inventory, slot)
    }
}
