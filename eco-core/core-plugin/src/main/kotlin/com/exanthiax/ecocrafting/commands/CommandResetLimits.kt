package com.exanthiax.ecocrafting.commands

import com.willfp.eco.core.command.impl.Subcommand
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.limit.service.CraftLimitService
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.command.CommandSender

internal fun recipeIdArgument(raw: String): String = raw.removePrefix("ecocrafting:")

class CommandResetLimits(
    private val plugin: EcoCraftingPlugin,
    private val recipeService: RecipeService,
    private val limitService: CraftLimitService
) : Subcommand(
    plugin,
    "resetlimits",
    "ecocrafting.admin",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(plugin.langYml.getMessage("limits-usage"))
            return
        }

        val recipeKey: NamespacedKey? = args.getOrNull(1)?.let(::recipeIdArgument)?.let { recipeId ->
            recipeService.keyOrWarn(recipeId)?.takeIf { recipeService.getMeta(it) != null } ?: run {
                sender.sendMessage(plugin.langYml.getMessage("limits-unknown-recipe").replace("%recipe%", recipeId))
                return
            }
        }
        val recipeName = recipeKey?.key ?: plugin.langYml.getFormattedString("messages.limits-all-recipes")

        when (args[0].lowercase()) {
            "global" -> {
                limitService.resetGlobal(recipeKey)
                sender.sendMessage(plugin.langYml.getMessage("limits-reset-global").replace("%recipe%", recipeName))
            }
            "all" -> {
                limitService.resetAllPlayers(recipeKey)
                sender.sendMessage(plugin.langYml.getMessage("limits-reset-all").replace("%recipe%", recipeName))
            }
            else -> {
                val target = Bukkit.getOfflinePlayerIfCached(args[0]) ?: run {
                    sender.sendMessage(plugin.langYml.getMessage("invalid-target"))
                    return
                }
                limitService.resetPlayer(target, recipeKey)
                sender.sendMessage(
                    plugin.langYml.getMessage("limits-reset-player")
                        .replace("%player%", target.name ?: args[0])
                        .replace("%recipe%", recipeName)
                )
            }
        }
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        return when (args.size) {
            1 -> listOf("global", "all") + Bukkit.getOnlinePlayers().map { it.name }
            2 -> recipeService.allKeys().filter { recipeService.getMeta(it)?.hasCraftLimit == true }.map { it.key }
            else -> emptyList()
        }
    }
}
