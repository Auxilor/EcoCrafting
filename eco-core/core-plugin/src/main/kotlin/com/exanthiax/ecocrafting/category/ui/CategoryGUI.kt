package com.exanthiax.ecocrafting.category.ui

import org.bukkit.entity.Player
import wiki.ecohub.modules.menus.menu.Menu

interface CategoryGUI {
    fun open(player: Player, page: Int, prevMenu: Menu?)
}
