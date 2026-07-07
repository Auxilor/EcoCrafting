package io.auxilor.ecocrafting.commands

import com.willfp.eco.core.command.impl.Subcommand
import io.auxilor.ecocrafting.BuildConfig
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
        PremiumGate.builderBlockMessage(BuildConfig.FREE_VERSION)?.let {
            sender.sendMessage(it)
            return
        }

        val player = sender as? Player ?: run {
            sender.sendMessage("Players only.")
            return
        }

        RecipeCreatorGUI.startWizard(player, args.getOrNull(0))
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        return RecipeCreatorGUI.stationTypeKeys
    }
}
