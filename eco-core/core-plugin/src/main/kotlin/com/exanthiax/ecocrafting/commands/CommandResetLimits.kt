package com.exanthiax.ecocrafting.commands

import com.exanthiax.ecocrafting.limit.service.CraftLimitService
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import wiki.ecohub.core.command.CommandBuilder
import wiki.ecohub.core.command.CommandContext
import wiki.ecohub.core.plugin.startup.Prerequisite

internal fun recipeIdArgument(raw: String): String = raw.removePrefix("ecocrafting:")

internal fun CommandBuilder<CommandSender>.resetLimitsCommand(
    recipeService: RecipeService,
    limitService: CraftLimitService
) = subcommand(
    name = "resetlimits",
    permission = "ecocrafting.admin"
) {
    executes {
        if (args.isEmpty()) {
            fail("limits-usage")
        }

        val recipeId = args.getOrNull(1)?.let(::recipeIdArgument)
        val recipeKey = recipeId?.let { id ->
            recipeService.keyOrWarn(id)?.takeIf { recipeService.getMeta(it) != null }
        }

        if (recipeId != null && recipeKey == null) {
            reply("limits-unknown-recipe", "recipe" to recipeId)
        } else {
            resetLimits(limitService, recipeKey)
        }
    }

    completes {
        when (args.size) {
            1 -> listOf("global", "all") + playerNames()
            2 -> recipeService.allKeys().filter { recipeService.getMeta(it)?.hasCraftLimit == true }.map { it.key }
            else -> emptyList()
        }
    }
}

private fun CommandContext<CommandSender>.resetLimits(limitService: CraftLimitService, recipeKey: NamespacedKey?) {
    val recipeName = recipeKey?.key ?: plugin.langYml.getFormattedString("messages.limits-all-recipes")

    when (args[0].lowercase()) {
        "global" -> {
            limitService.resetGlobal(recipeKey)
            reply("limits-reset-global", "recipe" to recipeName)
        }
        "all" -> {
            limitService.resetAllPlayers(recipeKey)
            reply("limits-reset-all", "recipe" to recipeName)
        }
        else -> {
            val target = cachedOfflinePlayer(args[0]) ?: fail("invalid-target")
            limitService.resetPlayer(target, recipeKey)
            reply("limits-reset-player", "player" to (target.name ?: args[0]), "recipe" to recipeName)
        }
    }
}

private fun cachedOfflinePlayer(name: String): OfflinePlayer? =
    if (Prerequisite.HAS_PAPER.isMet) {
        Bukkit.getOfflinePlayerIfCached(name)
    } else {
        Bukkit.getOfflinePlayers().firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
