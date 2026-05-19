package ru.oftendev.recipebook.custom.libreforge

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.conditions.Condition
import com.willfp.libreforge.triggers.TriggerData
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import ru.oftendev.recipebook.custom.CustomRecipes
import ru.oftendev.recipebook.custom.RecipeUnlockStore

object ConditionHasUnlockedRecipe : Condition<NoCompileData>("has_unlocked_recipe") {
    override fun isConditionMet(
        config: Config,
        player: Player,
        data: TriggerData,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ): Boolean {
        val recipeId = config.getString("args.recipe")
        val recipe = CustomRecipes.getByKey(NamespacedKey("recipebook", recipeId)) ?: return false
        return RecipeUnlockStore.isUnlocked(player, recipe)
    }
}
