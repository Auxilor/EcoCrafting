package io.auxilor.ecocrafting.gui.utils

import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import io.auxilor.ecocrafting.craft.QuickCraftService
import io.auxilor.ecocrafting.integration.ShopIntegration
import io.auxilor.ecocrafting.plugin
import io.auxilor.ecocrafting.recipe.ResolvedRecipe
import org.bukkit.entity.Player

fun RecipeGUIContext.buildPurchaseSlot(player: Player, recipe: ResolvedRecipe): Slot = with(this) {
    val service = QuickCraftService(player, recipe)
    val materialCounts = service.getMaterialCounts()
    val hasAllMaterials = materialCounts.all { it.has >= it.needs }
    val loreLines = config.getFormattedStrings("buttons.purchase-ingredients.lore").toMutableList()
    val materialsIdx = loreLines.indexOfFirst { it.contains("%materials%") }
    if (materialsIdx != -1) {
        loreLines.removeAt(materialsIdx)
        loreLines.addAll(materialsIdx, materialCounts.map { it.toLoreLine(player) })
    }

    fun sendPurchaseResult(target: Player, success: Boolean, message: String) {
        if (success) {
            target.sendMessage(plugin.langYml.getFormattedString("messages.craft-purchased")
                .replace("%item%", recipe.output.type.name.lowercase().replace("_", " ")))
            sound("purchase-success")?.playTo(target)
        } else {
            target.sendMessage(message)
            sound("purchase-fail")?.playTo(target)
        }
    }

    Slot.builder(
        ItemStackBuilder(Items.lookup(config.getString("buttons.purchase-ingredients.item")))
            .addLoreLines(loreLines)
            .withGlobalFlags()
            .build()
    ).onLeftClick { event, _ ->
        val target = event.whoClicked as Player
        if (hasAllMaterials) {
            target.sendMessage(plugin.langYml.getFormattedString("messages.craft-sufficient"))
            sound("purchase-success")?.playTo(target)
            return@onLeftClick
        }
        if (!ShopIntegration.isEnabled()) {
            target.sendMessage(plugin.langYml.getFormattedString("messages.shop-disabled"))
            sound("purchase-fail")?.playTo(target)
            return@onLeftClick
        }
        val result = ShopIntegration.purchaseMaterials(target, QuickCraftService(target, recipe).getMissingMaterials())
        sendPurchaseResult(target, result.success, result.message)
    }.onShiftLeftClick { event, _ ->
        val target = event.whoClicked as Player
        if (!ShopIntegration.canAutoBuy(true)) {
            target.sendMessage(plugin.langYml.getFormattedString("messages.shop-disabled"))
            return@onShiftLeftClick
        }
        val result = ShopIntegration.purchaseMaterials(target, QuickCraftService(target, recipe).getMissingMaterials())
        sendPurchaseResult(target, result.success, result.message)
    }.build()
}
