package com.exanthiax.ecocrafting.commands

import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.runOnGlobalRegion
import org.bukkit.command.CommandSender
import wiki.ecohub.core.command.CommandBuilder
import wiki.ecohub.core.stdlib.toNiceString
import wiki.ecohub.ui.text.StringUtils

internal fun CommandBuilder<CommandSender>.reloadCommand(recipeService: RecipeService) = subcommand(
    name = "reload",
    permission = "ecocrafting.command.reload"
) {
    executes {
        plugin.runOnGlobalRegion {
            sender.sendMessage(
                plugin.langYml.getMessage("reloaded", StringUtils.FormatOption.WITHOUT_PLACEHOLDERS)
                    .replace(oldValue = "%time%", newValue = plugin.reloadWithTime().toNiceString())
                    .replace(oldValue = "%count%", newValue = recipeService.allKeys().size.toString())
            )
        }
    }
}
