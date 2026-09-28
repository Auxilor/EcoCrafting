package com.exanthiax.ecocrafting.limit.integration

import com.willfp.eco.core.placeholder.PlayerDynamicPlaceholder
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.limit.model.remainingUnder
import com.exanthiax.ecocrafting.limit.service.CraftLimitService
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import java.util.regex.Pattern

enum class LimitStat(val suffix: String) {
    GLOBAL_USED("global_used"),
    GLOBAL_REMAINING("global_remaining"),
    GLOBAL_MAX("global_max"),
    USED("used"),
    REMAINING("remaining"),
    MAX("max")
}

fun parseLimitPlaceholder(args: String, recipeExists: (String) -> Boolean = { true }): Pair<String, LimitStat>? {
    if (!args.startsWith("limit_")) return null
    val body = args.removePrefix("limit_")
    val candidates = LimitStat.entries
        .filter { body.endsWith("_${it.suffix}") && body.length > it.suffix.length + 1 }
        .map { body.removeSuffix("_${it.suffix}") to it }
    return candidates.firstOrNull { recipeExists(it.first) } ?: candidates.firstOrNull()
}

fun registerCraftLimitPlaceholders(
    plugin: EcoCraftingPlugin,
    recipeService: RecipeService,
    limitService: CraftLimitService
) {
    PlayerDynamicPlaceholder(plugin, Pattern.compile("limit_.+")) { args, player ->
        val (recipeId, stat) = parseLimitPlaceholder(args) { candidate ->
            recipeService.keyOrWarn(candidate)?.let { recipeService.getMeta(it) } != null
        } ?: return@PlayerDynamicPlaceholder null
        val key = recipeService.keyOrWarn(recipeId) ?: return@PlayerDynamicPlaceholder ""
        val meta = recipeService.getMeta(key) ?: return@PlayerDynamicPlaceholder ""
        val unlimited = plugin.langYml.getFormattedString("messages.limit-unlimited")
        when (stat) {
            LimitStat.USED -> limitService.playerCrafts(player, key).toString()
            LimitStat.GLOBAL_USED -> limitService.globalCrafts(key).toString()
            LimitStat.MAX -> meta.playerCraftLimit.takeIf { it >= 0 }?.toString() ?: unlimited
            LimitStat.GLOBAL_MAX -> meta.globalCraftLimit.takeIf { it >= 0 }?.toString() ?: unlimited
            LimitStat.REMAINING -> meta.playerCraftLimit.takeIf { it >= 0 }
                ?.let { remainingUnder(it, limitService.playerCrafts(player, key)).toString() } ?: unlimited
            LimitStat.GLOBAL_REMAINING -> meta.globalCraftLimit.takeIf { it >= 0 }
                ?.let { remainingUnder(it, limitService.globalCrafts(key)).toString() } ?: unlimited
        }
    }.register()
}
