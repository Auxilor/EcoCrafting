package com.exanthiax.ecocrafting.recipe.service

import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.api.recipe.RecipesManager
import com.exanthiax.ecocrafting.recipe.model.EcoCraftingMeta
import org.bukkit.NamespacedKey
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class RecipeService(private val plugin: EcoCraftingPlugin) : RecipesManager {
    private val meta: MutableMap<NamespacedKey, EcoCraftingMeta> = Collections.synchronizedMap(linkedMapOf())
    private val variantToBase = ConcurrentHashMap<NamespacedKey, NamespacedKey>()
    private val autoUnlockKeys: MutableSet<NamespacedKey> = Collections.synchronizedSet(linkedSetOf())
    private val warnedInvalidRecipeIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // Bukkit-key suffixes eco appends to its registered crafting recipes.
    private val ecoKeySuffixes = listOf("_displayed", "_crafter")

    fun register(key: NamespacedKey, meta: EcoCraftingMeta) {
        this.meta[key] = meta
        if (meta.lockedByDefault && meta.unlockConditions.isNotEmpty()) autoUnlockKeys += key else autoUnlockKeys -= key
    }

    fun getMeta(key: NamespacedKey): EcoCraftingMeta? = meta[key]

    fun allKeys(): Set<NamespacedKey> = synchronized(meta) { meta.keys.toSet() }

    fun allMeta(): Collection<EcoCraftingMeta> = synchronized(meta) { meta.values.toList() }

    fun autoUnlockKeys(): Set<NamespacedKey> = synchronized(autoUnlockKeys) { autoUnlockKeys.toSet() }

    override fun allRecipeKeys(): Set<NamespacedKey> = allKeys()

    override fun isCustomRecipe(key: NamespacedKey): Boolean = getMeta(key) != null

    fun registerVariant(variantKey: NamespacedKey, baseKey: NamespacedKey) {
        variantToBase[variantKey] = baseKey
    }

    override fun baseKeyForVariant(key: NamespacedKey): NamespacedKey = variantToBase[key] ?: key

    // Normalizes ANY eco-generated crafting-recipe key to its base recipe key: strips the
    // `_displayed`/`_crafter` Bukkit suffixes eco appends when registering, then maps a
    // symmetry-variant key (`<id>_rot90`, `<id>_rot90_displayed`, ...) back to the base
    // recipe it was generated from. This is the single point every craft/resolve path uses
    // so rotated placements inherit the base recipe's lock/price/conditions and collapse to
    // one entry in the recipe book.
    fun resolveBaseKey(key: NamespacedKey): NamespacedKey {
        val stripped = ecoKeySuffixes.firstOrNull { key.key.endsWith(it) }
            ?.let { NamespacedKey(key.namespace, key.key.removeSuffix(it)) }
            ?: key
        return baseKeyForVariant(stripped)
    }

    fun clear() {
        meta.clear()
        variantToBase.clear()
        autoUnlockKeys.clear()
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
