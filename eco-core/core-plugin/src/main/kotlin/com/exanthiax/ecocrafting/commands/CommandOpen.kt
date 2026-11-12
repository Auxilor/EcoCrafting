package com.exanthiax.ecocrafting.commands

import com.exanthiax.ecocrafting.category.integration.CategoryLoader
import com.exanthiax.ecocrafting.category.service.CategoryService
import com.exanthiax.ecocrafting.category.ui.CategoryCategoryGUI
import com.exanthiax.ecocrafting.category.ui.ItemCategoryGUI
import com.exanthiax.ecocrafting.recipegui.service.RecipeGuiServices
import com.exanthiax.ecocrafting.runOwned
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import wiki.ecohub.core.command.CommandBuilder
import wiki.ecohub.core.command.CommandContext

internal fun CommandBuilder<CommandSender>.openCommand(
    categoryLoader: CategoryLoader,
    categoryService: CategoryService,
    guiServices: RecipeGuiServices
) = subcommand(
    name = "open",
    permission = "ecocrafting.command.open"
) {
    executes {
        checkOpenTarget()

        val menuString = argument(0, "must-specify-category")
        val category = categoryLoader.getByID(menuString) ?: fail("invalid-category")
        val target = Bukkit.getPlayer(args.getOrElse(1) { sender.name }) ?: fail("invalid-target")

        val gui = if (category.type == "items") {
            ItemCategoryGUI(category.config.getSubsection("gui"), category, categoryService, guiServices)
        } else {
            CategoryCategoryGUI(
                category.config.getSubsection("gui"),
                categoryLoader,
                categoryService,
                guiServices,
                category.categories
            )
        }
        target.runOwned(plugin) { gui.open(target, 1, null) }
    }

    completes {
        when (args.size) {
            1 -> categoryLoader.values().map { it.id }
            2 -> playerNames()
            else -> emptyList()
        }
    }
}

/**
 * Console must name a target, and opening for someone else needs `ecocrafting.open.others`.
 */
internal fun CommandContext<CommandSender>.checkOpenTarget() {
    if (sender !is Player && args.size < 2) {
        fail("not-player")
    }

    if (args.size >= 2 && !sender.hasPermission("ecocrafting.open.others")) {
        fail("no-permission")
    }
}
