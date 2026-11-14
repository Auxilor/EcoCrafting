package com.exanthiax.ecocrafting.unlock.integration

import com.exanthiax.ecocrafting.recipe.model.EcoCraftingMeta
import org.bukkit.NamespacedKey
import wiki.ecohub.rules.condition.ConditionList
import wiki.ecohub.rules.effect.EffectList
import wiki.ecohub.rules.holder.Holder

class RecipeUnlockHolder(
    val recipeKey: NamespacedKey,
    val meta: EcoCraftingMeta,
    override val effects: EffectList
) : Holder {
    override val id = NamespacedKey(recipeKey.namespace, "unlock_${recipeKey.key}")

    override val conditions: ConditionList = meta.unlockConditions
}
