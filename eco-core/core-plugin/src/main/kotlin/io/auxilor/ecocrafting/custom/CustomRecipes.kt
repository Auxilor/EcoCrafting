package io.auxilor.ecocrafting.custom

import com.willfp.libreforge.conditions.ConditionList
import com.willfp.libreforge.effects.Chain
import io.auxilor.ecocrafting.recipe.RecipeDisplayType
import org.bukkit.NamespacedKey

data class EcoCraftingMeta(
    val giveResultItem: Boolean,
    val effectsChain: Chain?,
    val visibilityConditions: ConditionList,
    val craftingConditions: ConditionList,
    val lockedByDefault: Boolean,
    val showWhenLocked: Boolean,
    val lockedLore: List<String>,
    val unlockConditions: ConditionList,
    val displayType: RecipeDisplayType,
    val supportCrafter: Boolean = false,
    val categoryId: String? = null
)

object CustomRecipes {
    private val meta = mutableMapOf<NamespacedKey, EcoCraftingMeta>()
    private val variantToBase = mutableMapOf<NamespacedKey, NamespacedKey>()

    fun register(key: NamespacedKey, meta: EcoCraftingMeta) {
        this.meta[key] = meta
    }

    fun getMeta(key: NamespacedKey): EcoCraftingMeta? = meta[key]

    fun allKeys(): Set<NamespacedKey> = meta.keys

    // Records that [variantKey] is a generated symmetry variant of [baseKey].
    fun registerVariant(variantKey: NamespacedKey, baseKey: NamespacedKey) {
        variantToBase[variantKey] = baseKey
    }

    // Returns the base recipe key for a tracked symmetry-variant key, or [key] unchanged.
    fun baseKeyForVariant(key: NamespacedKey): NamespacedKey = variantToBase[key] ?: key

    fun clear() {
        meta.clear()
        variantToBase.clear()
    }
}
