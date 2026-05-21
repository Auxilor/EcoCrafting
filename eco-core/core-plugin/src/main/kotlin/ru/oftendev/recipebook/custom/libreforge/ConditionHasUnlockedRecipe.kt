package ru.oftendev.recipebook.custom.libreforge

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.conditions.Condition
import com.willfp.libreforge.get
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import ru.oftendev.recipebook.custom.CustomRecipes
import ru.oftendev.recipebook.custom.RecipeUnlockStore

object ConditionHasUnlockedRecipe : Condition<NoCompileData>("has_unlocked_recipe") {
    override fun isMet(
        dispatcher: Dispatcher<*>,
        config: Config,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ): Boolean {
        val player = dispatcher.get<Player>() ?: return false
        val recipeId = config.getString("args.recipe")
        val key = NamespacedKey("recipebook", recipeId)
        WorkstationRecipes.getByKey(key) ?: return false
        val meta = CustomRecipes.getMeta(key) ?: return false
        return RecipeUnlockStore.isUnlocked(player, key, meta)
    }
}
