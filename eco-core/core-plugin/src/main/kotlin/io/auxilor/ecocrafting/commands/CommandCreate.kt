package io.auxilor.ecocrafting.commands

import com.willfp.eco.core.command.impl.Subcommand
import io.auxilor.ecocrafting.gui.RecipeCreatorGUI
import io.auxilor.ecocrafting.plugin
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object CommandCreate : Subcommand(
    plugin,
    "create",
    "ecocrafting.admin.create",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        val player = sender as? Player ?: run { sender.sendMessage("Players only."); return }
        RecipeCreatorGUI.openTypeSelect(player)
    }
}
