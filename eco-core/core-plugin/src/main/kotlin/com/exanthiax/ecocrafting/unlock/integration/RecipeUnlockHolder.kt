package com.exanthiax.ecocrafting.unlock.integration

import com.exanthiax.ecocrafting.recipe.model.EcoCraftingMeta
import com.willfp.libreforge.Holder
import com.willfp.libreforge.conditions.ConditionList
import com.willfp.libreforge.effects.EffectList
import org.bukkit.NamespacedKey

class RecipeUnlockHolder(
    val recipeKey: NamespacedKey,
    val meta: EcoCraftingMeta,
    override val effects: EffectList
) : Holder {
    override val id = NamespacedKey(recipeKey.namespace, "unlock_${recipeKey.key}")

    override val conditions: ConditionList = meta.unlockConditions
}
