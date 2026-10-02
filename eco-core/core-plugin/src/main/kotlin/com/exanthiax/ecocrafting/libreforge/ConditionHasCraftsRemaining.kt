package com.exanthiax.ecocrafting.libreforge

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.arguments
import com.willfp.libreforge.conditions.Condition
import com.willfp.libreforge.get
import com.exanthiax.ecocrafting.limit.service.CraftLimitService
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import org.bukkit.entity.Player

object ConditionHasCraftsRemaining : Condition<NoCompileData>("has_crafts_remaining") {
    lateinit var recipeService: RecipeService
    lateinit var limitService: CraftLimitService

    override val description = "Passes when the player can still craft the given custom recipe under its craft limits."

    override val categories = setOf("crafting")

    override val arguments = arguments {
        require(
            "recipe",
            "You must specify the recipe!",
            description = "The ID of the custom recipe to check.",
            type = ArgType.STRING,
            example = "epic_sword"
        )
        optional(
            "amount",
            description = "The minimum number of crafts that must remain.",
            type = ArgType.INT,
            default = "1",
            example = 1
        )
    }

    override fun isMet(
        dispatcher: Dispatcher<*>,
        config: Config,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ): Boolean {
        val player = dispatcher.get<Player>() ?: return false
        val key = recipeService.keyOrWarn(config.getString("recipe")) ?: return false
        val meta = recipeService.getMeta(key) ?: return false
        return limitService.craftsAllowed(player, key, meta) >= (config.getIntOrNull("amount") ?: 1)
    }
}
