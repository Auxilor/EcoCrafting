package com.exanthiax.ecocrafting.libreforge

import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.entity.Player
import wiki.ecohub.config.ArgType
import wiki.ecohub.config.Config
import wiki.ecohub.config.arguments
import wiki.ecohub.core.registry.NoCompileData
import wiki.ecohub.modules.recipe.workstation.WorkstationRecipes
import wiki.ecohub.rules.condition.Condition
import wiki.ecohub.rules.holder.Dispatcher
import wiki.ecohub.rules.holder.ProvidedHolder
import wiki.ecohub.rules.holder.get

object ConditionHasUnlockedRecipe : Condition<NoCompileData>("has_unlocked_recipe") {
    lateinit var recipeService: RecipeService
    lateinit var unlockService: RecipeUnlockService

    override val description = "Passes when the player has unlocked the given custom recipe."

    override val categories = setOf("crafting")

    override val arguments = arguments {
        require(
            "recipe",
            "You must specify the recipe!",
            description = "The ID of the custom recipe to check.",
            type = ArgType.STRING,
            example = "epic_sword"
        )
    }

    override fun isMet(
        dispatcher: Dispatcher<*>,
        config: Config,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ): Boolean {
        val player = dispatcher.get<Player>() ?: return false
        val recipeId = config.getString("recipe")
        val key = recipeService.keyOrWarn(recipeId) ?: return false
        if (recipeService.groupOutputs(key) != null) return unlockService.isUnlocked(player, key)
        WorkstationRecipes.getByKey(key) ?: return false
        val meta = recipeService.getMeta(key) ?: return false
        return unlockService.isUnlocked(player, key, meta)
    }
}
