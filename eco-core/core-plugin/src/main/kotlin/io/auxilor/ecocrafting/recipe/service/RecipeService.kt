package io.auxilor.ecocrafting.recipe.service

import io.auxilor.ecocrafting.EcoCraftingPlugin
import io.auxilor.ecocrafting.api.recipe.RecipesManager
import io.auxilor.ecocrafting.recipe.model.EcoCraftingMeta
import org.bukkit.NamespacedKey

// Registry of custom-recipe metadata, keyed by the recipe's NamespacedKey.
class RecipeService(private val plugin: EcoCraftingPlugin) : RecipesManager {
    private val meta = mutableMapOf<NamespacedKey, EcoCraftingMeta>()
    private val variantToBase = mutableMapOf<NamespacedKey, NamespacedKey>()
    private val warnedInvalidRecipeIds = mutableSetOf<String>()

    fun register(key: NamespacedKey, meta: EcoCraftingMeta) {
        this.meta[key] = meta
    }

    fun getMeta(key: NamespacedKey): EcoCraftingMeta? = meta[key]

    fun allKeys(): Set<NamespacedKey> = meta.keys

    override fun allRecipeKeys(): Set<NamespacedKey> = allKeys()

    override fun isCustomRecipe(key: NamespacedKey): Boolean = getMeta(key) != null

    // Records that [variantKey] is a generated symmetry variant of [baseKey].
    fun registerVariant(variantKey: NamespacedKey, baseKey: NamespacedKey) {
        variantToBase[variantKey] = baseKey
    }

    // Returns the base recipe key for a tracked symmetry-variant key, or [key] unchanged.
    override fun baseKeyForVariant(key: NamespacedKey): NamespacedKey = variantToBase[key] ?: key

    fun clear() {
        meta.clear()
        variantToBase.clear()
    }

    // Builds an "ecocrafting"-namespaced NamespacedKey from a config-supplied recipe id,
    // lowercasing it first since NamespacedKey rejects uppercase. Returns null (and logs a
    // one-time warning per offending value) instead of throwing on an invalid id.
    fun keyOrWarn(recipeId: String): NamespacedKey? {
        return try {
            NamespacedKey("ecocrafting", recipeId.lowercase())
        } catch (e: IllegalArgumentException) {
            if (warnedInvalidRecipeIds.add(recipeId)) {
                plugin.logger.warning("Invalid recipe id in config: '$recipeId'")
            }
            null
        }
    }
}
