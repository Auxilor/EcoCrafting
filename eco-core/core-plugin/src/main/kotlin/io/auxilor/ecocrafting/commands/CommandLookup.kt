package io.auxilor.ecocrafting.commands

import com.willfp.eco.core.command.impl.Subcommand
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.recipe.parts.EmptyTestableItem
import io.auxilor.ecocrafting.custom.CustomRecipes
import io.auxilor.ecocrafting.gui.RecipeGUI
import io.auxilor.ecocrafting.plugin
import io.auxilor.ecocrafting.recipe.RecipeResolver
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object CommandLookup : Subcommand(
    plugin,
    "lookup",
    "ecocrafting.command.lookup",
    true
) {
    override fun onExecute(player: Player, args: List<String>) {
        val item = Items.lookup(args.joinToString(" "))
        if (item is EmptyTestableItem) {
            player.sendMessage(plugin.langYml.getMessage("invalid-item"))
            return
        }
        if (RecipeResolver.resolve(item.item) == null) {
            player.sendMessage(plugin.langYml.getMessage("no-recipe"))
            return
        }
        RecipeGUI(item.item).open(player, null)
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        return CustomRecipes.allKeys().map { it.key }
    }
}
