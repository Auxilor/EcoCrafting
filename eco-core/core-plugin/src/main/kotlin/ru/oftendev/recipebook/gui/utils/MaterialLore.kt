package ru.oftendev.recipebook.gui.utils

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import ru.oftendev.recipebook.craft.MaterialCount
import ru.oftendev.recipebook.integration.ShopIntegration
import ru.oftendev.recipebook.recipeBookPlugin

fun MaterialCount.toLoreLine(player: Player): String {
    val itemName = if (item.hasItemMeta() && item.itemMeta.hasDisplayName()) {
        item.itemMeta.displayName()
            ?.let { LegacyComponentSerializer.legacySection().serialize(it) }
            ?: item.type.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }
    } else {
        item.type.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }
    }
    val langKey = if (has >= needs) "messages.material-sufficient" else "messages.material-missing"
    var line = recipeBookPlugin.langYml.getString(langKey)
        .replace("%has%", has.toString())
        .replace("%needs%", needs.toString())
        .replace("%item%", itemName)
    if (has < needs && ShopIntegration.shouldShowPrices()) {
        val info = ShopIntegration.getMaterialShopInfo(player, item, needs - has)
        if (info != null) {
            val priceKey = if (info.canBuy) "messages.shop-price-affordable" else "messages.shop-price-unaffordable"
            line += recipeBookPlugin.langYml.getString(priceKey)
                .replace("%price%", info.priceDisplay.ifBlank { info.status })
        }
    }
    return line
}
