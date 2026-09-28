package com.exanthiax.ecocrafting.recipe.model

import com.willfp.libreforge.conditions.ConditionList
import com.exanthiax.ecocrafting.limit.model.UNLIMITED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EcoCraftingMetaLimitTest {

    private fun meta(playerCraftLimit: Int = UNLIMITED, globalCraftLimit: Int = UNLIMITED) = EcoCraftingMeta(
        effectsChain = null,
        visibilityConditions = ConditionList(emptyList()),
        craftingConditions = ConditionList(emptyList()),
        lockedByDefault = false,
        showWhenLocked = false,
        lockedLore = emptyList(),
        unlockConditions = ConditionList(emptyList()),
        displayType = RecipeDisplayType.CRAFTING,
        playerCraftLimit = playerCraftLimit,
        globalCraftLimit = globalCraftLimit
    )

    @Test
    fun `limits default to unlimited`() {
        assertEquals(UNLIMITED, meta().playerCraftLimit)
        assertEquals(UNLIMITED, meta().globalCraftLimit)
        assertFalse(meta().hasCraftLimit)
    }

    @Test
    fun `any non-negative limit counts as a limit, including zero`() {
        assertTrue(meta(playerCraftLimit = 0).hasCraftLimit)
        assertTrue(meta(globalCraftLimit = 100).hasCraftLimit)
    }
}
