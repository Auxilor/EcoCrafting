package ru.oftendev.recipebook.gui

import com.willfp.eco.core.gui.menu.Menu
import org.bukkit.entity.Player

interface CategoryGUI {
    fun open(player: Player, page: Int, prevMenu: Menu?)
}