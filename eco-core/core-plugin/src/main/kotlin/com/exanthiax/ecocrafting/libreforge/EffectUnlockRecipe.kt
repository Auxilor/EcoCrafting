package com.exanthiax.ecocrafting.libreforge

import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import wiki.ecohub.config.ArgType
import wiki.ecohub.config.Config
import wiki.ecohub.config.arguments
import wiki.ecohub.core.registry.NoCompileData
import wiki.ecohub.game.entity.toDispatcher
import wiki.ecohub.rules.effect.Effect
import wiki.ecohub.rules.trigger.TriggerData
import wiki.ecohub.rules.trigger.TriggerParameter

object EffectUnlockRecipe : Effect<NoCompileData>("unlock_recipe") {
    lateinit var recipeService: RecipeService
    lateinit var unlockService: RecipeUnlockService

    override val description = "Unlocks a custom recipe for the player, allowing them to craft it."

    override val categories = setOf("crafting")

    override val parameters = setOf(
        TriggerParameter.PLAYER
    )

    override val arguments = arguments {
        require(
            "recipe",
            "You must specify the recipe!",
            description = "The ID of the custom recipe to unlock.",
            type = ArgType.STRING,
            example = "epic_sword"
        )
    }

    override fun onTrigger(
        config: Config,
        data: TriggerData,
        compileData: NoCompileData
    ): Boolean {
        val player = data.player ?: return false
        val recipeId = config.getString("recipe")
        val key = recipeService.keyOrWarn(recipeId) ?: return false
        if (!unlockService.unlock(player, key)) return false
        TriggerRecipeUnlocked.dispatch(
            player.toDispatcher(),
            data.copy(text = key.toString())
        )
        return true
    }
}
