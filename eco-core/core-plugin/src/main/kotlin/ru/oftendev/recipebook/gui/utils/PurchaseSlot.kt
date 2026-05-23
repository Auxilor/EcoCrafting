package ru.oftendev.recipebook.gui.utils

import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import org.bukkit.entity.Player
import ru.oftendev.recipebook.craft.QuickCraftService
import ru.oftendev.recipebook.integration.ShopIntegration
import ru.oftendev.recipebook.recipe.ResolvedRecipe
import ru.oftendev.recipebook.recipeBookPlugin

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
            target.sendMessage(recipeBookPlugin.langYml.getFormattedString("messages.craft-purchased")
                .replace("%item%", recipe.output.type.name.lowercase().replace("_", " ")))
            sound("purchase-success")?.playTo(target)
        } else {
            target.sendMessage(recipeBookPlugin.langYml.getFormattedString("messages.craft-failed")
                .replace("%reason%", message))
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
            target.sendMessage(recipeBookPlugin.langYml.getFormattedString("messages.craft-sufficient"))
            sound("purchase-success")?.playTo(target)
            return@onLeftClick
        }
        if (!ShopIntegration.isEnabled()) {
            target.sendMessage(recipeBookPlugin.langYml.getFormattedString("messages.shop-disabled"))
            sound("purchase-fail")?.playTo(target)
            return@onLeftClick
        }
        val result = ShopIntegration.purchaseMaterials(target, QuickCraftService(target, recipe).getMissingMaterials())
        sendPurchaseResult(target, result.success, result.message)
    }.onShiftLeftClick { event, _ ->
        val target = event.whoClicked as Player
        if (!ShopIntegration.canAutoBuy(true)) {
            target.sendMessage(recipeBookPlugin.langYml.getFormattedString("messages.shop-disabled"))
            return@onShiftLeftClick
        }
        val result = ShopIntegration.purchaseMaterials(target, QuickCraftService(target, recipe).getMissingMaterials())
        sendPurchaseResult(target, result.success, result.message)
    }.build()
}
