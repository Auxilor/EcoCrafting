package ru.oftendev.recipebook.commands

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.command.impl.Subcommand
import com.willfp.eco.core.items.Items
import org.bukkit.entity.Player
import ru.oftendev.recipebook.recipe.RecipeResolver

class CommandDebug(plugin: EcoPlugin) : Subcommand(plugin, "debug", "recipebook.command.debug", true) {
    override fun onExecute(sender: Player, args: List<String>) {
        val item = sender.inventory.itemInMainHand
        val custom = Items.getCustomItem(item)
        val recipe = RecipeResolver.resolve(item)

        sender.sendMessage(plugin.langYml.getFormattedString("messages.debug-header"))
        sender.sendMessage("&7Material: &f${item.type.name}")
        sender.sendMessage("&7Custom item: &f${custom?.key ?: "none"}")
        sender.sendMessage("&7Recipe key: &f${recipe?.key ?: "none"}")
        sender.sendMessage("&7Recipe source: &f${recipe?.source ?: "none"}")
        sender.sendMessage("&7Ingredients: &f${recipe?.ingredients?.count { !it.empty } ?: 0}")
    }
}
