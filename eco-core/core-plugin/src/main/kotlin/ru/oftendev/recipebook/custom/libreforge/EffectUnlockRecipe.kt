package ru.oftendev.recipebook.custom.libreforge

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import ru.oftendev.recipebook.custom.CustomRecipes
import ru.oftendev.recipebook.custom.RecipeUnlockStore

object EffectUnlockRecipe : Effect<NoCompileData>("unlock_recipe") {
    override val parameters = setOf(TriggerParameter.PLAYER)

    override fun onTrigger(
        config: Config,
        data: TriggerData,
        compileData: NoCompileData
    ): Boolean {
        val player = data.player ?: return false
        val recipeId = config.getString("args.recipe")
        val recipe = CustomRecipes.getByKey(NamespacedKey("recipebook", recipeId)) ?: return false
        RecipeUnlockStore.unlock(player, recipe)
        TriggerRecipeUnlocked.dispatch(
            player.toDispatcher(),
            data.copy(text = recipe.key.toString())
        )
        return true
    }
}
