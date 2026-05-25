package ru.oftendev.recipebook.commands

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.command.impl.PluginCommand
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ru.oftendev.recipebook.gui.CategoryCategoryGUI
import ru.oftendev.recipebook.recipeBookPlugin

class MainCommand(plugin: EcoPlugin) : PluginCommand(plugin, "recipebook",
    "recipebook.command.recipebook", false) {
    init {
        this.addSubcommand(CommandReload(plugin))
            .addSubcommand(CommandLookup(plugin))
            .addSubcommand(CommandOpen(plugin))
            .addSubcommand(CommandDebug(plugin))
            .addSubcommand(CommandValidate(plugin))
            .addSubcommand(CommandList(plugin))
            .addSubcommand(CommandCreate(plugin))
            .addSubcommand(CommandUnlock(plugin))
            .addSubcommand(CommandLock(plugin))
            .addSubcommand(CommandConfirm(plugin))
            .addSubcommand(CommandCancel(plugin))
    }

    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (args.isEmpty() && sender is Player) {
            CategoryCategoryGUI(recipeBookPlugin.configYml.getSubsection("category-browser-gui"))
                .open(sender, 1, null)
        } else {
            sender.sendMessage(
                plugin.langYml.getMessage("invalid-command")
            )
        }
    }
}