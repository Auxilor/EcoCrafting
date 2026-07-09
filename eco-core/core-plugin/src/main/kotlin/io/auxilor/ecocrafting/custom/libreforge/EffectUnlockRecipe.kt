package io.auxilor.ecocrafting.custom.libreforge

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.arguments
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import io.auxilor.ecocrafting.custom.CustomRecipes
import io.auxilor.ecocrafting.custom.RecipeUnlockStore
import io.auxilor.ecocrafting.recipe.invalidRecipeIdKeyOrWarn

object EffectUnlockRecipe : Effect<NoCompileData>("unlock_recipe") {
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
        val key = invalidRecipeIdKeyOrWarn(recipeId) ?: return false
        val meta = CustomRecipes.getMeta(key) ?: return false
        RecipeUnlockStore.unlock(player, key, meta)
        TriggerRecipeUnlocked.dispatch(
            player.toDispatcher(),
            data.copy(text = key.toString())
        )
        return true
    }
}
