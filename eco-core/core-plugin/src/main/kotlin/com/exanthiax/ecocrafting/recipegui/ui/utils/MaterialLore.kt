package com.exanthiax.ecocrafting.recipegui.ui.utils

import com.exanthiax.ecocrafting.quickcraft.service.MaterialCount
import com.exanthiax.ecocrafting.recipegui.service.RecipeGuiServices
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.ItemMeta
import wiki.ecohub.core.plugin.startup.Prerequisite

fun MaterialCount.toLoreLine(player: Player, services: RecipeGuiServices): String {
    val plugin = services.plugin
    val meta = item.itemMeta
    val fallbackName = item.type.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }
    val itemName = meta?.legacyName() ?: fallbackName
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

/**
 * Custom-item plugins (Nexo etc.) commonly set the item_name component rather than
 * the legacy display-name NBT, so check it first or their names fall back to the
 * base material here.
 */
internal fun ItemMeta.legacyName(): String? = when {
    hasItemName() -> if (Prerequisite.HAS_PAPER.isMet) {
        LegacyComponentSerializer.legacySection().serialize(itemName())
    } else {
        itemName
    }
    hasDisplayName() -> if (Prerequisite.HAS_PAPER.isMet) {
        displayName()?.let { LegacyComponentSerializer.legacySection().serialize(it) }
    } else {
        displayName
    }
    else -> null
}
