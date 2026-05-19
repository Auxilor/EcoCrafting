package ru.oftendev.recipebook.commands

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.command.impl.Subcommand
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.recipe.parts.EmptyTestableItem
import org.bukkit.entity.Player
import ru.oftendev.recipebook.gui.RecipeGUI
import ru.oftendev.recipebook.recipe.RecipeResolver
import ru.oftendev.recipebook.recipeBookPlugin

class CommandLookup(plugin: EcoPlugin) : Subcommand(plugin, "lookup", "recipebook.command.lookup", true) {
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
