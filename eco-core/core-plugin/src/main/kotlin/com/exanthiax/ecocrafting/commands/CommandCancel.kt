package com.exanthiax.ecocrafting.commands

import com.willfp.eco.core.command.impl.Subcommand
import com.exanthiax.ecocrafting.BuildConfig
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.recipegui.ui.RecipeCreatorGUI
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CommandCancel(
    plugin: EcoCraftingPlugin,
    private val recipeCreatorGUI: RecipeCreatorGUI
) : Subcommand(
    plugin,
    "cancel",
    "ecocrafting.admin.create",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        PremiumGate.builderBlockMessage(BuildConfig.FREE_VERSION)?.let {
            sender.sendMessage(it)
            return
        }

        val player = sender as? Player ?: return

        recipeCreatorGUI.cancelSave(player)
    }
}
