package com.exanthiax.ecocrafting.commands

import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.runOwned
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import wiki.ecohub.core.command.CommandBuilder
import wiki.ecohub.modules.recipe.workstation.WorkstationRecipes

/**
 * `unlock` and `lock`: [change] the recipe for the player, then tell the sender it was [done].
 */
internal fun CommandBuilder<CommandSender>.recipeAccessCommand(
    name: String,
    done: String,
    recipeService: RecipeService,
    change: (Player, NamespacedKey) -> Unit
) = subcommand(
    name = name,
    permission = "ecocrafting.admin"
) {
    executes {
        val target = args.getOrNull(0)?.let { Bukkit.getPlayer(it) }
        val recipeId = args.getOrNull(1).orEmpty()
        val key = runCatching { NamespacedKey("ecocrafting", recipeId.lowercase()) }.getOrNull()

        when {
            args.size < 2 -> sender.sendMessage("Usage: /ecocrafting $name <player> <recipe-id>")
            target == null -> sender.sendMessage("Player not found or offline.")
            key == null -> sender.sendMessage("Invalid recipe id: $recipeId")
            !recipeService.isKnownRecipe(key) -> sender.sendMessage("Unknown recipe: $recipeId")
            else -> target.runOwned(plugin) {
                change(target, key)
                sender.sendMessage("$done '$recipeId' for ${target.name}.")
            }
        }
    }

    completes {
        when (args.size) {
            1 -> playerNames()
            2 -> (recipeService.groupKeys() + recipeService.allKeys()).map { it.key }
            else -> emptyList()
        }
    }
}

private fun RecipeService.isKnownRecipe(key: NamespacedKey): Boolean =
    groupOutputs(key) != null || WorkstationRecipes.getByKey(key) != null && getMeta(key) != null
