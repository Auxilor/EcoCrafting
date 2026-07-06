package io.auxilor.ecocrafting.gui.utils

import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.util.formatEco
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import io.auxilor.ecocrafting.craft.CraftAttempt
import io.auxilor.ecocrafting.craft.QuickCraftService
import io.auxilor.ecocrafting.custom.CustomRecipes
import io.auxilor.ecocrafting.integration.ShopIntegration
import io.auxilor.ecocrafting.recipe.RecipeSource
import io.auxilor.ecocrafting.recipe.ResolvedRecipe
import io.auxilor.ecocrafting.plugin

fun RecipeGUIContext.buildQuickCraftSlot(player: Player, recipe: ResolvedRecipe): Slot = with(this) {
    val meta = if (recipe.source == RecipeSource.CUSTOM && recipe.key != null) {
        CustomRecipes.getMeta(recipe.key)
    } else null
    val service = QuickCraftService(player, recipe)
    val materialCounts = service.getMaterialCounts()
    val loreLines = if (recipe.locked && meta != null && meta.showWhenLocked && meta.lockedLore.isNotEmpty()) {
        meta.lockedLore.toMutableList()
    } else {
        config.getFormattedStrings("buttons.quick-craft.lore").toMutableList().also { lines ->
            val materialsIdx = lines.indexOfFirst { it.contains("%materials%") }
            if (materialsIdx != -1) {
                lines.removeAt(materialsIdx)
                lines.addAll(materialsIdx, materialCounts.map { it.toLoreLine(player) })
            }
        }
    }

    fun finish(event: InventoryClickEvent, target: Player, result: CraftAttempt, purchased: Boolean) {
        if (result.success) {
            val key = if (purchased) "messages.craft-purchased" else "messages.craft-success"
            target.sendMessage(plugin.langYml.getFormattedString(key)
                .replace("%item%", recipe.output.type.name.lowercase().replace("_", " ")))
            sound("quick-craft-success")?.playTo(target)
            target.closeInventory()
        } else {
            // Empty reason means checkCraftingConditions already messaged the
            // player (locked/conditions-not-met) — avoid a redundant generic message.
            if (result.reason.isNotEmpty()) {
                val key = if (result.reason == "No inventory space") "messages.craft-no-space" else "messages.craft-failed"
                target.sendMessage(plugin.langYml.getFormattedString(key)
                    .replace("%reason%", result.reason)
                    .formatEco(target))
            }
            sound("quick-craft-fail")?.playTo(target)
        }
    }

    fun retryAfterPurchase(event: InventoryClickEvent, target: Player, attempts: Int = 0) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val result = QuickCraftService(target, recipe).craft()
            if (result.success || result.reason != "Missing materials" || attempts >= 20)
                finish(event, target, result, true)
            else
                retryAfterPurchase(event, target, attempts + 1)
        }, 1L)
    }

    fun handle(event: InventoryClickEvent) {
        val target = event.whoClicked as Player
        if (recipe.locked) {
            target.sendMessage(plugin.langYml.getFormattedString("messages.recipe-locked"))
            sound("quick-craft-fail")?.playTo(target)
            return
        }
        val liveService = QuickCraftService(target, recipe)
        var result = liveService.craft()
        if (!result.success && result.reason == "Missing materials") {
            if (ShopIntegration.canAutoBuy(event.isShiftClick)) {
                val purchase = ShopIntegration.purchaseMaterials(target, liveService.getMissingMaterials())
                if (purchase.success) { retryAfterPurchase(event, target); return }
                result = result.copy(reason = purchase.message)
            } else if (event.isShiftClick && ShopIntegration.isEnabled()) {
                result = result.copy(reason = plugin.langYml.getString("messages.shop-auto-buy-disabled"))
            }
        }
        finish(event, target, result, false)
    }

    Slot.builder(
        ItemStackBuilder(Items.lookup(config.getString("buttons.quick-craft.item")))
            .addLoreLines(loreLines)
            .withGlobalFlags()
            .build()
    ).onLeftClick { event, _ -> handle(event) }
     .onShiftLeftClick { event, _ -> handle(event) }
     .build()
}
