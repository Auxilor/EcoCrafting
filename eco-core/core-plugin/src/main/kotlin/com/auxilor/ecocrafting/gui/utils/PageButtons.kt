package com.auxilor.ecocrafting.gui.utils

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import org.bukkit.inventory.ItemStack

internal fun pageButtonItem(config: Config, basePath: String, state: String): ItemStack? {
    val itemString = config.getStringOrNull("$basePath.item.$state") ?: return null
    return ItemStackBuilder(Items.lookup(itemString))
        .addLoreLines(config.getFormattedStrings("$basePath.lore.$state"))
        .build()
}
