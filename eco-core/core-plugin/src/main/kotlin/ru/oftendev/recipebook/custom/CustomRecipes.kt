package ru.oftendev.recipebook.custom

import com.willfp.libreforge.conditions.ConditionList
import com.willfp.libreforge.effects.Chain
import org.bukkit.NamespacedKey
import ru.oftendev.recipebook.recipe.RecipeDisplayType

data class RecipeBookMeta(
    val giveResultItem: Boolean,
    val effectsChain: Chain?,
    val visibilityConditions: ConditionList,
    val craftingConditions: ConditionList,
    val lockedByDefault: Boolean,
    val showWhenLocked: Boolean,
    val lockedLore: List<String>,
    val unlockConditions: ConditionList,
    val displayType: RecipeDisplayType,
    val supportCrafter: Boolean = false
)

object CustomRecipes {
    private val meta = mutableMapOf<NamespacedKey, RecipeBookMeta>()

    fun register(key: NamespacedKey, m: RecipeBookMeta) {
        meta[key] = m
    }

    fun getMeta(key: NamespacedKey): RecipeBookMeta? = meta[key]

    fun allKeys(): Set<NamespacedKey> = meta.keys

    fun clear() {
        meta.clear()
    }
}
