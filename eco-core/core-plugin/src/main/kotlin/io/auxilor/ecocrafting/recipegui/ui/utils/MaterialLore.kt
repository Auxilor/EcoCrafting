package io.auxilor.ecocrafting.recipegui.ui.utils

import io.auxilor.ecocrafting.quickcraft.service.MaterialCount
import io.auxilor.ecocrafting.recipegui.service.RecipeGuiServices
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player

fun MaterialCount.toLoreLine(player: Player, services: RecipeGuiServices): String {
    val plugin = services.plugin
    val itemName = if (item.hasItemMeta() && item.itemMeta.hasDisplayName()) {
        item.itemMeta.displayName()
            ?.let { LegacyComponentSerializer.legacySection().serialize(it) }
            ?: item.type.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }
    } else {
        item.type.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }
    }
    val langKey = if (has >= needs) "messages.material-sufficient" else "messages.material-missing"
    var line = plugin.langYml.getString(langKey)
        .replace("%has%", has.toString())
        .replace("%needs%", needs.toString())
        .replace("%item%", itemName)
    if (has < needs && services.shopService.shouldShowPrices()) {
        val info = services.shopService.getMaterialShopInfo(player, item, needs - has)
        if (info != null) {
            val priceKey = if (info.canBuy) "messages.shop-price-affordable" else "messages.shop-price-unaffordable"
            line += plugin.langYml.getString(priceKey)
                .replace("%price%", info.priceDisplay.ifBlank { info.status })
        }
    }
    return line
}
