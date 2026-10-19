package com.exanthiax.ecocrafting.category.ui

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import org.bukkit.inventory.ItemStack

internal fun Config.pageChangerItem(base: String, active: Boolean): ItemStack {
    val state = getActive(active)
    return ItemStackBuilder(
        Items.lookup(getString("buttons.$base.item.$state"))
    ).addLoreLines(
        getFormattedStrings("buttons.$base.lore.$state")
    ).build()
}

internal fun getActive(active: Boolean): String {
    return if (active) "active" else "inactive"
}
