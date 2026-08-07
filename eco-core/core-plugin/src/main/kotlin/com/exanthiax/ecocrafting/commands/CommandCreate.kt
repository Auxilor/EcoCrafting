package com.exanthiax.ecocrafting.commands

import com.willfp.eco.core.command.impl.Subcommand
import com.exanthiax.ecocrafting.BuildConfig
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.recipegui.ui.RecipeCreatorGUI
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CommandCreate(
    plugin: EcoCraftingPlugin,
    private val recipeCreatorGUI: RecipeCreatorGUI
) : Subcommand(
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

        recipeCreatorGUI.startWizard(player, args.getOrNull(0))
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        if (args.size > 1) {
            return emptyList()
        }

        return recipeCreatorGUI.stationTypeKeys
    }
}
