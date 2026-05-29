package com.auxilor.ecocrafting.commands

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.command.impl.Subcommand
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.recipe.parts.EmptyTestableItem
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import com.auxilor.ecocrafting.custom.CustomRecipes
import com.auxilor.ecocrafting.gui.RecipeGUI
import com.auxilor.ecocrafting.recipe.RecipeResolver
import com.auxilor.ecocrafting.plugin

class CommandLookup(plugin: EcoPlugin) : Subcommand(plugin, "lookup", "EcoCrafting.command.lookup", true) {
    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        return CustomRecipes.allKeys().map { it.key }
    }

    override fun onExecute(sender: Player, args: List<String>) {
        val item = Items.lookup(args.joinToString(" "))
        if (item is EmptyTestableItem) {
            sender.sendMessage(plugin.langYml.getMessage("invalid-item"))
            return
        }
        if (RecipeResolver.resolve(item.item) == null) {
            sender.sendMessage(plugin.langYml.getMessage("no-recipe"))
            return
        }
        RecipeGUI(item.item).open(sender, null)
    }
}
