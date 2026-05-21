package ru.oftendev.recipebook.custom.libreforge

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.NamespacedKey
import ru.oftendev.recipebook.custom.CustomRecipes
import ru.oftendev.recipebook.custom.RecipeUnlockStore

object EffectLockRecipe : Effect<NoCompileData>("lock_recipe") {
    override val parameters = setOf(TriggerParameter.PLAYER)

    override fun onTrigger(
        config: Config,
        data: TriggerData,
        compileData: NoCompileData
    ): Boolean {
        val player = data.player ?: return false
        val recipeId = config.getString("args.recipe")
        val key = NamespacedKey("recipebook", recipeId)
        val meta = CustomRecipes.getMeta(key) ?: return false
        RecipeUnlockStore.lock(player, key, meta)
        TriggerRecipeLocked.dispatch(
            player.toDispatcher(),
            data.copy(text = key.toString())
        )
        return true
    }
}
