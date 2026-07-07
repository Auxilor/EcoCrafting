package io.auxilor.ecocrafting.commands

import com.willfp.eco.core.command.impl.Subcommand
import com.willfp.eco.core.items.Items
import io.auxilor.ecocrafting.plugin
import io.auxilor.ecocrafting.recipe.RecipeResolver
import org.bukkit.entity.Player

object CommandDebug : Subcommand(
    plugin,
    "debug",
    "ecocrafting.command.debug",
    true
) {
    override fun onExecute(player: Player, args: List<String>) {
        val item = player.inventory.itemInMainHand
        val custom = Items.getCustomItem(item)
        val recipe = RecipeResolver.resolve(item)

        player.sendMessage(plugin.langYml.getFormattedString("messages.debug-header"))
        player.sendMessage("&7Material: &f${item.type.name}")
        player.sendMessage("&7Custom item: &f${custom?.key ?: "none"}")
        player.sendMessage("&7Recipe key: &f${recipe?.key ?: "none"}")
        player.sendMessage("&7Recipe source: &f${recipe?.source ?: "none"}")
        player.sendMessage("&7Ingredients: &f${recipe?.ingredients?.count { !it.empty } ?: 0}")
    }
}
