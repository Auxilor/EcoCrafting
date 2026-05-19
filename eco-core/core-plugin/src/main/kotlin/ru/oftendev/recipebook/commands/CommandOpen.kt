package ru.oftendev.recipebook.commands

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.command.impl.Subcommand
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ru.oftendev.recipebook.category.RecipeCategories

class CommandOpen(plugin: EcoPlugin) : Subcommand(plugin, "open",
    "recipebook.command.open", false) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (sender !is Player && args.size < 2) {
            sender.sendMessage(plugin.langYml.getMessage("not-player"))
            return
        }

        if (args.size >= 2 && !sender.hasPermission("recipebook.open.others")) {
            sender.sendMessage(plugin.langYml.getMessage("no-permission"))
            return
        }

        val menuString = args.firstOrNull() ?: run {
            sender.sendMessage(plugin.langYml.getMessage("must-specify-category"))
            return
        }

        val menu = RecipeCategories.getById(menuString) ?: run {
            sender.sendMessage(plugin.langYml.getMessage("invalid-category"))
            return
        }

        val targetString = args.getOrElse(1) { sender.name }

        val target = Bukkit.getPlayer(targetString) ?: run {
            sender.sendMessage(plugin.langYml.getMessage("invalid-target"))
            return
        }

        menu.gui.open(target, 1, null)
    }
}
