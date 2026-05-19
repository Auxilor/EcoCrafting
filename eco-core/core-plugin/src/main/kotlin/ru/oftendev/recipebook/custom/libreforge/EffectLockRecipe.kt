package ru.oftendev.recipebook.custom.libreforge

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import com.willfp.libreforge.toDispatcher
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import ru.oftendev.recipebook.custom.CustomRecipes
import ru.oftendev.recipebook.custom.RecipeUnlockStore

object EffectLockRecipe : Effect<NoCompileData>("lock_recipe") {
    override val parameters = setOf(TriggerParameter.PLAYER)

    override fun onTrigger(
        config: Config,
        player: Player,
        data: TriggerData,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ): Boolean {
        val recipeId = config.getString("args.recipe")
        val recipe = CustomRecipes.getByKey(NamespacedKey("recipebook", recipeId)) ?: return false
        RecipeUnlockStore.lock(player, recipe)
        TriggerRecipeLocked.dispatch(
            player.toDispatcher(),
            data.copy(text = recipe.key.toString())
        )
        return true
    }
}
