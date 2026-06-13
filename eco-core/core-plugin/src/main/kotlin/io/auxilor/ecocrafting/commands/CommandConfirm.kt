package io.auxilor.ecocrafting.commands

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.command.impl.Subcommand
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import io.auxilor.ecocrafting.gui.RecipeCreatorGUI

class CommandConfirm(plugin: EcoPlugin) : Subcommand(plugin, "confirm", "EcoCrafting.admin.create", false) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        val player = sender as? Player ?: return
        RecipeCreatorGUI.confirmSave(player)
    }
}
