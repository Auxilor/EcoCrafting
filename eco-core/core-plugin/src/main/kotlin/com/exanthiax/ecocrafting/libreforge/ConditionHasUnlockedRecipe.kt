package com.exanthiax.ecocrafting.libreforge

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.arguments
import com.willfp.libreforge.conditions.Condition
import com.willfp.libreforge.get
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.entity.Player

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
        WorkstationRecipes.getByKey(key) ?: return false
        val meta = recipeService.getMeta(key) ?: return false
        return unlockService.isUnlocked(player, key, meta)
    }
}
