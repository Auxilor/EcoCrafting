package com.auxilor.ecocrafting.custom

import com.willfp.libreforge.conditions.ConditionList
import com.willfp.libreforge.effects.Chain
import org.bukkit.NamespacedKey
import com.auxilor.ecocrafting.recipe.RecipeDisplayType

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

    fun register(key: NamespacedKey, m: EcoCraftingMeta) {
        meta[key] = m
    }

    fun getMeta(key: NamespacedKey): EcoCraftingMeta? = meta[key]

    fun allKeys(): Set<NamespacedKey> = meta.keys

    fun clear() {
        meta.clear()
    }
}
