package com.auxilor.ecocrafting.commands

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.command.impl.Subcommand
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import com.auxilor.ecocrafting.gui.RecipeCreatorGUI

class CommandCreate(plugin: EcoPlugin) : Subcommand(plugin, "create", "EcoCrafting.admin.create", false) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        val player = sender as? Player ?: run { sender.sendMessage("Players only."); return }
        RecipeCreatorGUI.openTypeSelect(player)
    }
}
