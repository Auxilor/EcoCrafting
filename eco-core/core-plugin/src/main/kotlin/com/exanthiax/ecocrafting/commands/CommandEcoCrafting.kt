package com.exanthiax.ecocrafting.commands

import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.category.integration.CategoryLoader
import com.exanthiax.ecocrafting.category.service.CategoryService
import com.exanthiax.ecocrafting.category.ui.CategoryCategoryGUI
import com.exanthiax.ecocrafting.recipegui.service.RecipeGuiServices
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import wiki.ecohub.core.command.CommandBuilder
import wiki.ecohub.core.command.EcoCommand
import wiki.ecohub.core.command.command

internal fun EcoCraftingPlugin.ecoCraftingCommand(
    categoryLoader: CategoryLoader,
    categoryService: CategoryService,
    guiServices: RecipeGuiServices,
    subcommands: CommandBuilder<CommandSender>.() -> Unit
): EcoCommand = command(name = "ecocrafting", permission = "ecocrafting.command.ecocrafting") {
    subcommands()

    executes {
        val player = sender as? Player

        if (args.isEmpty() && player != null) {
            CategoryCategoryGUI(
                configYml.getSubsection("category-browser-gui"),
                categoryLoader,
                categoryService,
                guiServices
            ).open(player, 1, null)
        } else {
            reply("invalid-command")
        }
    }
}
