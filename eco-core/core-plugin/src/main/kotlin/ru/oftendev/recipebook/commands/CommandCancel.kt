package ru.oftendev.recipebook.commands

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.command.impl.Subcommand
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ru.oftendev.recipebook.gui.RecipeCreatorGUI

class CommandCancel(plugin: EcoPlugin) : Subcommand(plugin, "cancel", "recipebook.admin.create", false) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        val player = sender as? Player ?: return
        RecipeCreatorGUI.cancelSave(player)
    }
}
