package io.auxilor.ecocrafting.custom.libreforge

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.conditions.Condition
import com.willfp.libreforge.get
import io.auxilor.ecocrafting.custom.CustomRecipes
import io.auxilor.ecocrafting.custom.RecipeUnlockStore
import io.auxilor.ecocrafting.recipe.invalidRecipeIdKeyOrWarn
import org.bukkit.entity.Player

object ConditionHasUnlockedRecipe : Condition<NoCompileData>("has_unlocked_recipe") {
    override fun isMet(
        dispatcher: Dispatcher<*>,
        config: Config,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ): Boolean {
        val player = dispatcher.get<Player>() ?: return false
        val recipeId = config.getString("args.recipe")
        val key = invalidRecipeIdKeyOrWarn(recipeId) ?: return false
        WorkstationRecipes.getByKey(key) ?: return false
        val meta = CustomRecipes.getMeta(key) ?: return false
        return RecipeUnlockStore.isUnlocked(player, key, meta)
    }
}
