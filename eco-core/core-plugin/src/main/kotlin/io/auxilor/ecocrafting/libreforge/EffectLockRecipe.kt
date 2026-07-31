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

// libreforge looks effects up by name from a global registry, so this must stay an
// `object`; recipeService/unlockService are wired once by the composition root
// (an explicit assignment, not init-block side effects) before Effects.register() runs.
object EffectLockRecipe : Effect<NoCompileData>("lock_recipe") {
    lateinit var recipeService: RecipeService
    lateinit var unlockService: RecipeUnlockService

    override val description = "Locks a custom recipe for the player, preventing them from crafting it."

    override val categories = setOf("crafting")

    override val parameters = setOf(
        TriggerParameter.PLAYER
    )

    override val arguments = arguments {
        require(
            "recipe",
            "You must specify the recipe!",
            description = "The ID of the custom recipe to lock.",
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
        val meta = recipeService.getMeta(key) ?: return false
        unlockService.lock(player, key, meta)
        TriggerRecipeLocked.dispatch(
            player.toDispatcher(),
            data.copy(text = key.toString())
        )
        return true
    }
}
