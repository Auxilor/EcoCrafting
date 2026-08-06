package com.exanthiax.ecocrafting.commands

import com.willfp.eco.core.command.impl.Subcommand
import com.willfp.eco.util.StringUtils
import com.willfp.eco.util.toNiceString
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import org.bukkit.command.CommandSender

class CommandReload(
    private val plugin: EcoCraftingPlugin,
    private val recipeService: RecipeService
) : Subcommand(
    plugin,
    "reload",
    "ecocrafting.command.reload",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        sender.sendMessage(
            plugin.langYml.getMessage("reloaded", StringUtils.FormatOption.WITHOUT_PLACEHOLDERS)
                .replace("%time%", plugin.reloadWithTime().toNiceString())
                .replace("%count%", recipeService.allKeys().size.toString())
        )
    }
}
