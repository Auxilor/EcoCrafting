package io.auxilor.ecocrafting.commands

import com.willfp.eco.core.command.impl.Subcommand
import com.willfp.eco.util.formatEco
import io.auxilor.ecocrafting.BuildConfig
import io.auxilor.ecocrafting.gui.RecipeCreatorGUI
import io.auxilor.ecocrafting.plugin
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object CommandEdit : Subcommand(
    plugin,
    "edit",
    "ecocrafting.admin.edit",
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

        val id = args.getOrNull(0) ?: run {
            player.sendMessage("&cUsage: /ecocrafting edit <id>".formatEco())
            return
        }

        RecipeCreatorGUI.startEditWizard(player, id)
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        if (args.size > 1) {
            return emptyList()
        }

        return RecipeCreatorGUI.existingRecipeIds()
    }
}
