package io.auxilor.ecocrafting.commands

import com.willfp.eco.core.command.impl.PluginCommand
import com.willfp.eco.core.command.impl.Subcommand
import io.auxilor.ecocrafting.EcoCraftingPlugin
import io.auxilor.ecocrafting.category.integration.CategoryLoader
import io.auxilor.ecocrafting.category.service.CategoryService
import io.auxilor.ecocrafting.category.ui.CategoryCategoryGUI
import io.auxilor.ecocrafting.recipegui.service.RecipeGuiServices
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CommandEcoCrafting(
    private val plugin: EcoCraftingPlugin,
    private val categoryLoader: CategoryLoader,
    private val categoryService: CategoryService,
    private val guiServices: RecipeGuiServices,
    subcommands: List<Subcommand>
) : PluginCommand(
    plugin,
    "ecocrafting",
    "ecocrafting.command.ecocrafting",
    false
) {
    init {
        subcommands.forEach { addSubcommand(it) }
    }

    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (args.isEmpty() && sender is Player) {
            CategoryCategoryGUI(plugin.configYml.getSubsection("category-browser-gui"), categoryLoader, categoryService, guiServices)
                .open(sender, 1, null)
        } else {
            sender.sendMessage(plugin.langYml.getMessage("invalid-command"))
        }
    }
}
