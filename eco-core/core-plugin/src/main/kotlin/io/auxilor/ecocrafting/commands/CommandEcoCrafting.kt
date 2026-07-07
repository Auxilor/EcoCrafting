package io.auxilor.ecocrafting.commands

import com.willfp.eco.core.command.impl.PluginCommand
import io.auxilor.ecocrafting.gui.CategoryCategoryGUI
import io.auxilor.ecocrafting.plugin
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object CommandEcoCrafting : PluginCommand(
    plugin,
    "ecocrafting",
    "ecocrafting.command.ecocrafting",
    false
) {
    init {
        this.addSubcommand(CommandReload)
            .addSubcommand(CommandOpen)
            .addSubcommand(CommandCreate)
            .addSubcommand(CommandEdit)
            .addSubcommand(CommandUnlock)
            .addSubcommand(CommandLock)
            .addSubcommand(CommandConfirm)
            .addSubcommand(CommandCancel)
    }

    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (args.isEmpty() && sender is Player) {
            CategoryCategoryGUI(plugin.configYml.getSubsection("category-browser-gui"))
                .open(sender, 1, null)
        } else {
            sender.sendMessage(plugin.langYml.getMessage("invalid-command"))
        }
    }
}
