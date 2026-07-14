package io.auxilor.ecocrafting.crafting.integration

import com.willfp.eco.core.recipe.workstation.SmithingRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import io.auxilor.ecocrafting.EcoCraftingPlugin
import io.auxilor.ecocrafting.crafting.event.CustomSmithEvent
import io.auxilor.ecocrafting.crafting.service.checkCraftingConditions
import io.auxilor.ecocrafting.crafting.service.fireCraftEffects
import io.auxilor.ecocrafting.recipe.service.RecipeService
import io.auxilor.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.SmithingInventory
import org.bukkit.inventory.Inventory

class SmithingListener(
    private val plugin: EcoCraftingPlugin,
    private val recipeService: RecipeService,
    private val unlockService: RecipeUnlockService
) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onCraft(event: CraftItemEvent) {
        if (event.view.topInventory !is SmithingInventory) return

        val player = event.whoClicked as? Player ?: return
        val recipeKey = (event.recipe as? org.bukkit.Keyed)?.key ?: return
        val inventory = event.view.topInventory
        val recipe = (WorkstationRecipes.getByKey(recipeKey)
            ?: WorkstationRecipes.getAll(SmithingRecipe::class.java).firstOrNull {
                it.template.matches(inventory.getItem(0)) &&
                it.base.matches(inventory.getItem(1)) &&
                it.addition.matches(inventory.getItem(2))
            }) as? SmithingRecipe
            ?: run {
                plugin.debug("[Smithing] no recipe for key=$recipeKey")
                return
            }

        val meta = recipeService.getMeta(recipe.key) ?: return
        plugin.debug("[Smithing] onCraft: key=${recipe.key} giveResultItem=${meta.giveResultItem}")

        if (!checkCraftingConditions(plugin, unlockService, player, recipe, meta)) { event.isCancelled = true; return }

        val item = recipe.output?.clone() ?: return
        val customEvent = CustomSmithEvent(player, recipe, item)
        Bukkit.getPluginManager().callEvent(customEvent)
        if (customEvent.isCancelled) { event.isCancelled = true; return }

        if (!meta.giveResultItem) {
            event.isCancelled = true
            consumeSmithingSlots(event.view.topInventory)
            plugin.server.scheduler.runTask(plugin, Runnable { player.updateInventory() })
        }
        meta.price.pay(player, 1.0)
        fireCraftEffects(player, recipe, meta, item, 1, event.view.topInventory.location?.block)
        plugin.debug("[Smithing] effects fired for recipe=${recipe.key}")
    }

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
        if (meta.giveResultItem) return

        plugin.debug("[Smithing] onSmithingResultClick: no-item recipe=${recipe.key}")
        if (!checkCraftingConditions(plugin, unlockService, player, recipe, meta)) { event.isCancelled = true; return }

        event.isCancelled = true
        consumeSmithingSlots(inventory)
        meta.price.pay(player, 1.0)
        val item = recipe.output?.clone() ?: return
        val customEvent = CustomSmithEvent(player, recipe, item)
        Bukkit.getPluginManager().callEvent(customEvent)
        if (!customEvent.isCancelled) fireCraftEffects(player, recipe, meta, item, 1, inventory.location?.block)
        plugin.server.scheduler.runTask(plugin, Runnable { player.updateInventory() })
    }

    private fun consumeSmithingSlots(inventory: Inventory) {
        for (slot in 0..2) consume(inventory, slot)
    }
}
