package com.exanthiax.ecocrafting.recipe.model

import com.willfp.eco.core.price.ConfiguredPrice
import com.willfp.libreforge.conditions.ConditionList
import com.willfp.libreforge.effects.Chain

data class EcoCraftingMeta(
    // Omitted in config means the recipe hands over its result: only an explicit
    // give-result-item: false turns that off.
    val giveResultItem: Boolean = true,
    val effectsChain: Chain?,
    val visibilityConditions: ConditionList,
    val craftingConditions: ConditionList,
    val lockedByDefault: Boolean,
    val showWhenLocked: Boolean,
    val lockedLore: List<String>,
    val unlockConditions: ConditionList,
    val displayType: RecipeDisplayType,
    val supportCrafter: Boolean = false,
    val categoryId: String? = null,
    val price: ConfiguredPrice = ConfiguredPrice.FREE,
    // Villager trades only, and only in a command-opened merchant: 0 means unlimited uses.
    val maxUses: Int = 0
)
