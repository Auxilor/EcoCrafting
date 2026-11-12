package com.exanthiax.ecocrafting.commands

import com.exanthiax.ecocrafting.BuildConfig
import com.exanthiax.ecocrafting.recipegui.ui.RecipeCreatorGUI
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import wiki.ecohub.core.command.CommandBuilder
import wiki.ecohub.ui.text.formatEco

internal fun CommandBuilder<CommandSender>.editCommand(recipeCreatorGUI: RecipeCreatorGUI) = subcommand(
    name = "edit",
    permission = "ecocrafting.admin.edit"
) {
    executes {
        val blocked = PremiumGate.builderBlockMessage(BuildConfig.FREE_VERSION)
        val player = sender as? Player
        val id = args.getOrNull(0)

        when {
            blocked != null -> sender.sendMessage(blocked)
            player == null -> sender.sendMessage("Players only.")
            id == null -> player.sendMessage("&cUsage: /ecocrafting edit <id>".formatEco())
            else -> recipeCreatorGUI.startEditWizard(player, id)
        }
    }

    completes {
        if (args.size > 1) emptyList() else recipeCreatorGUI.existingRecipeIds()
    }
}
