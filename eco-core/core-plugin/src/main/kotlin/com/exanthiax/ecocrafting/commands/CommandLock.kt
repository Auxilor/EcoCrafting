package com.exanthiax.ecocrafting.commands

import com.willfp.eco.core.command.impl.Subcommand
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.command.CommandSender

class CommandLock(
    plugin: EcoCraftingPlugin,
    private val recipeService: RecipeService,
    private val unlockService: RecipeUnlockService
) : Subcommand(
    plugin,
    "lock",
    "ecocrafting.admin",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (args.size < 2) {
            sender.sendMessage("Usage: /ecocrafting lock <player> <recipe-id>")
            return
        }

        val target = Bukkit.getPlayer(args[0]) ?: run {
            sender.sendMessage("Player not found or offline.")
            return
        }

        val key = try {
            NamespacedKey("ecocrafting", args[1].lowercase())
        } catch (e: IllegalArgumentException) {
            sender.sendMessage("Invalid recipe id: ${args[1]}")
            return
        }

        WorkstationRecipes.getByKey(key) ?: run {
            sender.sendMessage("Unknown recipe: ${args[1]}")
            return
        }

        val meta = recipeService.getMeta(key) ?: run {
            sender.sendMessage("Unknown recipe: ${args[1]}")
            return
        }

        unlockService.lock(target, key, meta)

        sender.sendMessage("Locked '${args[1]}' for ${target.name}.")
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        return when (args.size) {
            1 -> Bukkit.getOnlinePlayers().map { it.name }
            2 -> recipeService.allKeys().map { it.key }
            else -> emptyList()
        }
    }
}
