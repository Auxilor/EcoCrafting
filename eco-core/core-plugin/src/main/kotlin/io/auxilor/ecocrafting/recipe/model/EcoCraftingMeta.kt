package io.auxilor.ecocrafting.recipe.model

import com.willfp.eco.core.price.ConfiguredPrice
import com.willfp.libreforge.conditions.ConditionList
import com.willfp.libreforge.effects.Chain

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
    val categoryId: String? = null,
    val price: ConfiguredPrice = ConfiguredPrice.FREE
)
