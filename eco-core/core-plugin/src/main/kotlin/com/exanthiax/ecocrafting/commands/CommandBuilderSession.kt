package com.exanthiax.ecocrafting.commands

import com.exanthiax.ecocrafting.BuildConfig
import com.exanthiax.ecocrafting.recipegui.ui.RecipeCreatorGUI
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import wiki.ecohub.core.command.CommandBuilder

internal fun CommandBuilder<CommandSender>.confirmCommand(recipeCreatorGUI: RecipeCreatorGUI) =
    builderSessionCommand("confirm") { recipeCreatorGUI.confirmSave(it) }

internal fun CommandBuilder<CommandSender>.cancelCommand(recipeCreatorGUI: RecipeCreatorGUI) =
    builderSessionCommand("cancel") { recipeCreatorGUI.cancelSave(it) }

private fun CommandBuilder<CommandSender>.builderSessionCommand(name: String, action: (Player) -> Unit) = subcommand(
    name = name,
    permission = "ecocrafting.admin.create"
) {
    executes {
        val blocked = PremiumGate.builderBlockMessage(BuildConfig.FREE_VERSION)

        if (blocked != null) {
            sender.sendMessage(blocked)
        } else {
            (sender as? Player)?.let(action)
        }
    }
}
