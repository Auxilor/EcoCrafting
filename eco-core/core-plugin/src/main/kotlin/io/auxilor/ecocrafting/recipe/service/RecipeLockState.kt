package io.auxilor.ecocrafting.recipe.service

import io.auxilor.ecocrafting.recipe.model.RecipeSource
import io.auxilor.ecocrafting.recipe.model.ResolvedRecipe
import io.auxilor.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.entity.Player

fun ResolvedRecipe.withLockState(
    player: Player,
    recipeService: RecipeService,
    unlockService: RecipeUnlockService
): ResolvedRecipe {
    if (source != RecipeSource.CUSTOM || key == null) return this
    val meta = recipeService.getMeta(key) ?: return this
    return copy(locked = unlockService.isLocked(player, key, meta))
}
