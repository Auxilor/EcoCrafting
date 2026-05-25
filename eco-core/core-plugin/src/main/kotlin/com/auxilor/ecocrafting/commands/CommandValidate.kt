package com.auxilor.ecocrafting.commands

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.command.impl.Subcommand
import org.bukkit.command.CommandSender
import com.auxilor.ecocrafting.category.RecipeCategories
import com.auxilor.ecocrafting.validation.CategoryValidator

class CommandValidate(plugin: EcoPlugin) : Subcommand(plugin, "validate", "EcoCrafting.command.validate", false) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        val report = CategoryValidator.buildReport()
        sender.sendMessage("&aEcoCrafting validation complete")
        sender.sendMessage("&7Categories: &f${report.categories}")
        sender.sendMessage("&7Item entries: &f${report.itemEntries}")
        if (report.warnings.isEmpty()) {
            sender.sendMessage("&aNo warnings found.")
        } else {
            sender.sendMessage("&eWarnings: &f${report.warnings.size}")
            report.warnings.take(12).forEach { sender.sendMessage("&e- &7$it") }
            if (report.warnings.size > 12) {
                sender.sendMessage("&7...and ${report.warnings.size - 12} more. Check console for full validation on reload.")
            }
        }
    }
}

class CommandList(plugin: EcoPlugin) : Subcommand(plugin, "list", "EcoCrafting.command.list", false) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        sender.sendMessage("&aEcoCrafting categories:")
        for (category in RecipeCategories.values.sortedBy { it.id }) {
            sender.sendMessage("&7- &f${category.id} &8(${category.type}, ${category.items.size} items, ${category.categories.size} children)")
        }
    }
}
