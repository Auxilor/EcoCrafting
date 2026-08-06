package com.exanthiax.ecocrafting.commands

import com.willfp.eco.core.command.impl.Subcommand
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.category.integration.CategoryLoader
import com.exanthiax.ecocrafting.category.service.CategoryService
import com.exanthiax.ecocrafting.category.ui.CategoryCategoryGUI
import com.exanthiax.ecocrafting.category.ui.ItemCategoryGUI
import com.exanthiax.ecocrafting.recipegui.service.RecipeGuiServices
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CommandOpen(
    private val plugin: EcoCraftingPlugin,
    private val categoryLoader: CategoryLoader,
    private val categoryService: CategoryService,
    private val guiServices: RecipeGuiServices
) : Subcommand(
    plugin,
    "open",
    "ecocrafting.command.open",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (sender !is Player && args.size < 2) {
            sender.sendMessage(plugin.langYml.getMessage("not-player"))
            return
        }

        if (args.size >= 2 && !sender.hasPermission("ecocrafting.open.others")) {
            sender.sendMessage(plugin.langYml.getMessage("no-permission"))
            return
        }

        val menuString = args.firstOrNull() ?: run {
            sender.sendMessage(plugin.langYml.getMessage("must-specify-category"))
            return
        }

        val category = categoryLoader.getByID(menuString) ?: run {
            sender.sendMessage(plugin.langYml.getMessage("invalid-category"))
            return
        }

        val targetString = args.getOrElse(1) { sender.name }

        val target = Bukkit.getPlayer(targetString) ?: run {
            sender.sendMessage(plugin.langYml.getMessage("invalid-target"))
            return
        }

        val gui = if (category.type == "items") {
            ItemCategoryGUI(category.config.getSubsection("gui"), category, categoryService, guiServices)
        } else {
            CategoryCategoryGUI(category.config.getSubsection("gui"), categoryLoader, categoryService, guiServices)
        }
        gui.open(target, 1, null)
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        return when (args.size) {
            1 -> categoryLoader.values().map { it.id }
            2 -> Bukkit.getOnlinePlayers().map { it.name }
            else -> emptyList()
        }
    }
}
