package io.auxilor.ecocrafting.recipegui.ui.utils

import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.util.formatEco
import io.auxilor.ecocrafting.quickcraft.service.CraftAttempt
import io.auxilor.ecocrafting.quickcraft.service.CraftFailure
import io.auxilor.ecocrafting.recipe.model.RecipeSource
import io.auxilor.ecocrafting.recipe.model.ResolvedRecipe
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

fun RecipeGUIContext.buildQuickCraftSlot(player: Player, recipe: ResolvedRecipe): Slot = with(this) {
    val plugin = services.plugin
    val meta = if (recipe.source == RecipeSource.CUSTOM && recipe.key != null) {
        services.recipeService.getMeta(recipe.key)
    } else null
    val service = services.quickCraft(player, recipe)
    val materialCounts = service.getMaterialCounts()
    val loreLines = if (recipe.locked && meta != null && meta.showWhenLocked && meta.lockedLore.isNotEmpty()) {
        meta.lockedLore.toMutableList()
    } else {
        config.getFormattedStrings("buttons.quick-craft.lore").toMutableList().also { lines ->
            val materialsIdx = lines.indexOfFirst { it.contains("%materials%") }
            if (materialsIdx != -1) {
                lines.removeAt(materialsIdx)
                lines.addAll(materialsIdx, materialCounts.map { it.toLoreLine(player, services) })
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
            // CraftFailure.None means checkCraftingConditions already messaged the
            // player (locked/conditions-not-met) - avoid a redundant generic message.
            val message = resolveFailureMessage(plugin, result.failure)
            if (message != null) {
                target.sendMessage(message.formatEco(target))
            }
            sound("quick-craft-fail")?.playTo(target)
        }
    }

    fun retryAfterPurchase(event: InventoryClickEvent, target: Player, attempts: Int = 0) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val result = services.quickCraft(target, recipe).craft()
            if (result.success || result.failure !is CraftFailure.MissingMaterials || attempts >= 20)
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
        val liveService = services.quickCraft(target, recipe)
        var result = liveService.craft()
        if (!result.success && result.failure is CraftFailure.MissingMaterials) {
            if (services.shopService.canAutoBuy(event.isShiftClick)) {
                val purchase = services.shopService.purchaseMaterials(target, liveService.getMissingMaterials())
                if (purchase.success) { retryAfterPurchase(event, target); return }
                result = result.copy(failure = CraftFailure.Custom(purchase.message))
            } else if (event.isShiftClick && services.shopService.isEnabled()) {
                result = result.copy(failure = CraftFailure.Custom(plugin.langYml.getFormattedString("messages.failed-reason.shop-auto-buy-disabled")))
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

fun resolveFailureMessage(plugin: io.auxilor.ecocrafting.EcoCraftingPlugin, failure: CraftFailure): String? = when (failure) {
    is CraftFailure.None -> null
    is CraftFailure.NoPermission -> plugin.langYml.getFormattedString("messages.failed-reason.no-permission")
    is CraftFailure.MissingMaterials -> plugin.langYml.getFormattedString("messages.failed-reason.missing-materials")
    is CraftFailure.NoSpace -> plugin.langYml.getFormattedString("messages.failed-reason.no-space")
    is CraftFailure.CannotAfford -> plugin.langYml.getFormattedString("messages.failed-reason.cannot-afford").replace("%price%", "")
    is CraftFailure.Custom -> failure.text
}
