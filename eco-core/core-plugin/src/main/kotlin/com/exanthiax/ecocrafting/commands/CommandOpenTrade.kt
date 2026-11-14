package com.exanthiax.ecocrafting.commands

import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.runOwned
import com.exanthiax.ecocrafting.trade.service.TradeMerchantFactory
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import wiki.ecohub.core.command.CommandBuilder
import wiki.ecohub.modules.recipe.workstation.VillagerRecipe
import wiki.ecohub.modules.recipe.workstation.WorkstationRecipes

// Splits the comma-separated recipe id argument. Order is preserved (it's the order trades
// appear in the merchant) and duplicates are kept - listing the same trade twice is a valid,
// if odd, thing for an admin to ask for.
internal fun parseTradeIds(argument: String?): List<String> =
    argument.orEmpty()
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

internal fun CommandBuilder<CommandSender>.openTradeCommand(
    recipeService: RecipeService,
    merchantFactory: TradeMerchantFactory
) = subcommand(
    name = "open-trade",
    permission = "ecocrafting.command.opentrade"
) {
    executes {
        checkOpenTarget()

        val ids = parseTradeIds(args.firstOrNull()).ifEmpty { fail("must-specify-trade") }
        val recipes = ids.map { it to recipeService.tradeRecipe(it) }
        val invalid = recipes.firstOrNull { it.second == null }?.first
        val target = Bukkit.getPlayer(args.getOrElse(1) { sender.name })

        when {
            invalid != null -> reply("invalid-trade", "recipe" to invalid)
            target == null -> fail("invalid-target")
            else -> target.runOwned(plugin) {
                if (!merchantFactory.open(target, recipes.mapNotNull { it.second })) {
                    reply("no-trades-available")
                }
            }
        }
    }

    completes {
        when (args.size) {
            1 -> WorkstationRecipes.getAll(VillagerRecipe::class.java)
                .filter { recipeService.getMeta(it.key) != null }
                .map { it.key.key }
            2 -> playerNames()
            else -> emptyList()
        }
    }
}

/**
 * The villager trade with [id], or null if there is none. One bad id aborts the whole command rather
 * than opening a partial GUI: an unknown id is an admin typo, and dropping it would hide the mistake.
 */
internal fun RecipeService.tradeRecipe(id: String): VillagerRecipe? =
    (keyOrWarn(id)?.let { WorkstationRecipes.getByKey(it) } as? VillagerRecipe)
        ?.takeIf { getMeta(it.key) != null }
