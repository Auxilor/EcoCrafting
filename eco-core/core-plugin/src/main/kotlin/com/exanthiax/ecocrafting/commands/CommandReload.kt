package com.exanthiax.ecocrafting.commands

import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.runOnGlobalRegion
import com.willfp.eco.core.command.impl.Subcommand
import org.bukkit.command.CommandSender
import wiki.ecohub.core.stdlib.toNiceString
import wiki.ecohub.ui.text.StringUtils

class CommandReload(
    private val plugin: EcoCraftingPlugin,
    private val recipeService: RecipeService
) : Subcommand(
    plugin,
    "reload",
    "ecocrafting.command.reload",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) = plugin.runOnGlobalRegion {
        sender.sendMessage(
            plugin.langYml.getMessage("reloaded", StringUtils.FormatOption.WITHOUT_PLACEHOLDERS)
                .replace("%time%", plugin.reloadWithTime().toNiceString())
                .replace("%count%", recipeService.allKeys().size.toString())
        )
    }
}
