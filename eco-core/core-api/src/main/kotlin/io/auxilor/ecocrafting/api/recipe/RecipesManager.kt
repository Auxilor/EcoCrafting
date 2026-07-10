package io.auxilor.ecocrafting.api.recipe

import org.bukkit.NamespacedKey

interface RecipesManager {
    fun allRecipeKeys(): Set<NamespacedKey>
    fun isCustomRecipe(key: NamespacedKey): Boolean
    // Symmetry variants (rotations/mirrors of a shaped recipe) resolve to their base key.
    fun baseKeyForVariant(key: NamespacedKey): NamespacedKey
}
