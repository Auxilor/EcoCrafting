package com.exanthiax.ecocrafting.commands

import com.exanthiax.ecocrafting.BuildConfig
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.recipegui.ui.RecipeCreatorGUI
import com.willfp.eco.core.command.impl.Subcommand
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import wiki.ecohub.ui.text.formatEco

class CommandEdit(
    plugin: EcoCraftingPlugin,
    private val recipeCreatorGUI: RecipeCreatorGUI
) : Subcommand(
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

        recipeCreatorGUI.startEditWizard(player, id)
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        if (args.size > 1) {
            return emptyList()
        }

        return recipeCreatorGUI.existingRecipeIds()
    }
}
