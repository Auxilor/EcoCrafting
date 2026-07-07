package io.auxilor.ecocrafting.custom.libreforge

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import io.auxilor.ecocrafting.custom.CustomRecipes
import io.auxilor.ecocrafting.custom.RecipeUnlockStore
import io.auxilor.ecocrafting.recipe.invalidRecipeIdKeyOrWarn
import org.bukkit.entity.Player

object EffectUnlockRecipe : Effect<NoCompileData>("unlock_recipe") {
    override val parameters = setOf(TriggerParameter.PLAYER)

    override fun onTrigger(
        config: Config,
        data: TriggerData,
        compileData: NoCompileData
    ): Boolean {
        val player = data.player ?: return false
        val recipeId = config.getString("args.recipe")
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
