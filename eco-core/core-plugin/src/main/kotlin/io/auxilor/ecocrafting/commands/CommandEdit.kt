package io.auxilor.ecocrafting.commands

import com.willfp.eco.core.command.impl.Subcommand
import com.willfp.eco.util.formatEco
import io.auxilor.ecocrafting.BuildConfig
import io.auxilor.ecocrafting.EcoCraftingPlugin
import io.auxilor.ecocrafting.recipegui.ui.RecipeCreatorGUI
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

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
