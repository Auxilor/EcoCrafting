package com.exanthiax.ecocrafting.category.ui

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.sound.AbstractPlayableSound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

internal fun Config.backSlot(menu: Menu, sound: AbstractPlayableSound<*>?): Slot {
    return Slot.builder(
        ItemStackBuilder(Items.lookup(getString("buttons.back.item")))
            .addLoreLines(getFormattedStrings("buttons.back.lore"))
            .build()
    )
        .onLeftClick { event, _ ->
            menu.open(event.whoClicked as Player)
            sound?.playTo(event.whoClicked as Player)
        }
        .build()
}

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
