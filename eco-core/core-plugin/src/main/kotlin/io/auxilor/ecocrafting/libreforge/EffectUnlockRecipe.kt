package io.auxilor.ecocrafting.libreforge

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.arguments
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import io.auxilor.ecocrafting.recipe.service.RecipeService
import io.auxilor.ecocrafting.unlock.service.RecipeUnlockService

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
            type = ArgType.STRING
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
        val meta = recipeService.getMeta(key) ?: return false
        unlockService.unlock(player, key, meta)
        TriggerRecipeUnlocked.dispatch(
            player.toDispatcher(),
            data.copy(text = key.toString())
        )
        return true
    }
}
