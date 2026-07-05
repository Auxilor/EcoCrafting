package io.auxilor.ecocrafting.commands

import com.willfp.eco.core.command.impl.Subcommand
import io.auxilor.ecocrafting.gui.RecipeCreatorGUI
import io.auxilor.ecocrafting.plugin
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object CommandCancel : Subcommand(
    plugin,
    "cancel",
    "ecocrafting.admin.create",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        val player = sender as? Player ?: return
        RecipeCreatorGUI.cancelSave(player)
    }
}
