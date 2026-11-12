package com.exanthiax.ecocrafting.commands

import com.exanthiax.ecocrafting.BuildConfig
import com.exanthiax.ecocrafting.recipegui.ui.RecipeCreatorGUI
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import wiki.ecohub.core.command.CommandBuilder

internal fun CommandBuilder<CommandSender>.createCommand(recipeCreatorGUI: RecipeCreatorGUI) = subcommand(
    name = "create",
    permission = "ecocrafting.admin.create"
) {
    executes {
        val blocked = PremiumGate.builderBlockMessage(BuildConfig.FREE_VERSION)
        val player = sender as? Player

        when {
            blocked != null -> sender.sendMessage(blocked)
            player == null -> sender.sendMessage("Players only.")
            else -> recipeCreatorGUI.startWizard(player, args.getOrNull(0))
        }
    }

    completes {
        if (args.size > 1) emptyList() else recipeCreatorGUI.stationTypeKeys
    }
}
