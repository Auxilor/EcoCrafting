package ru.oftendev.recipebook.commands

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.command.impl.Subcommand
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.command.CommandSender
import ru.oftendev.recipebook.custom.CustomRecipes
import ru.oftendev.recipebook.custom.RecipeUnlockStore

class CommandLock(plugin: EcoPlugin) : Subcommand(plugin, "lock", "recipebook.admin", true) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (args.size < 2) { sender.sendMessage("Usage: /recipebook lock <player> <recipe-id>"); return }
        val target = Bukkit.getPlayer(args[0]) ?: run { sender.sendMessage("Player not found or offline."); return }
        val recipe = CustomRecipes.getByKey(NamespacedKey("recipebook", args[1]))
            ?: run { sender.sendMessage("Unknown recipe: ${args[1]}"); return }
        RecipeUnlockStore.lock(target, recipe)
        sender.sendMessage("Locked '${args[1]}' for ${target.name}.")
    }
}
