package com.exanthiax.ecocrafting.commands

import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.trade.service.TradeMerchantFactory
import com.willfp.eco.core.command.impl.Subcommand
import com.willfp.eco.core.recipe.workstation.VillagerRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

// Splits the comma-separated recipe id argument. Order is preserved (it's the order trades
// appear in the merchant) and duplicates are kept - listing the same trade twice is a valid,
// if odd, thing for an admin to ask for.
internal fun parseTradeIds(argument: String?): List<String> =
    argument.orEmpty()
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

class CommandOpenTrade(
    private val plugin: EcoCraftingPlugin,
    private val recipeService: RecipeService,
    private val merchantFactory: TradeMerchantFactory
) : Subcommand(
    plugin,
    "open-trade",
    "ecocrafting.command.opentrade",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (sender !is Player && args.size < 2) {
            sender.sendMessage(plugin.langYml.getMessage("not-player"))
            return
        }

        if (args.size >= 2 && !sender.hasPermission("ecocrafting.open.others")) {
            sender.sendMessage(plugin.langYml.getMessage("no-permission"))
            return
        }

        val ids = parseTradeIds(args.firstOrNull())
        if (ids.isEmpty()) {
            sender.sendMessage(plugin.langYml.getMessage("must-specify-trade"))
            return
        }

        // Abort on the first bad id rather than opening a partial GUI - an unknown id is an
        // admin typo, and silently dropping it hides the mistake.
        val recipes = ids.map { id ->
            val key = recipeService.keyOrWarn(id)
            val recipe = key?.let { WorkstationRecipes.getByKey(it) } as? VillagerRecipe
            if (recipe == null || recipeService.getMeta(recipe.key) == null) {
                sender.sendMessage(plugin.langYml.getMessage("invalid-trade").replace("%recipe%", id))
                return
            }
            recipe
        }

        val targetString = args.getOrElse(1) { sender.name }

        val target = Bukkit.getPlayer(targetString) ?: run {
            sender.sendMessage(plugin.langYml.getMessage("invalid-target"))
            return
        }

        if (!merchantFactory.open(target, recipes)) {
            sender.sendMessage(plugin.langYml.getMessage("no-trades-available"))
        }
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        return when (args.size) {
            1 -> villagerRecipeIds()
            2 -> Bukkit.getOnlinePlayers().map { it.name }
            else -> emptyList()
        }
    }

    private fun villagerRecipeIds(): List<String> =
        WorkstationRecipes.getAll(VillagerRecipe::class.java)
            .filter { recipeService.getMeta(it.key) != null }
            .map { it.key.key }
}
