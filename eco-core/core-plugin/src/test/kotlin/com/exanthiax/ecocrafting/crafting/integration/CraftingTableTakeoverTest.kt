package com.exanthiax.ecocrafting.crafting.integration

import com.willfp.libreforge.conditions.ConditionList
import com.exanthiax.ecocrafting.limit.model.UNLIMITED
import com.exanthiax.ecocrafting.recipe.model.EcoCraftingMeta
import com.exanthiax.ecocrafting.recipe.model.RecipeDisplayType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CraftingTableTakeoverTest {

    private fun meta(
        giveResultItem: Boolean = true,
        supportCrafter: Boolean = false,
        playerCraftLimit: Int = UNLIMITED
    ) = EcoCraftingMeta(
        giveResultItem = giveResultItem,
        effectsChain = null,
        visibilityConditions = ConditionList(emptyList()),
        craftingConditions = ConditionList(emptyList()),
        lockedByDefault = false,
        showWhenLocked = false,
        lockedLore = emptyList(),
        unlockConditions = ConditionList(emptyList()),
        displayType = RecipeDisplayType.CRAFTING,
        supportCrafter = supportCrafter,
        playerCraftLimit = playerCraftLimit
    )

    @Test
    fun `plain recipe on the native path is left to vanilla`() {
        assertFalse(takesOverCraft(needsTakeover = false, meta = meta()))
    }

    @Test
    fun `limited recipe always takes over so vanilla craft-all cannot overshoot`() {
        assertTrue(takesOverCraft(needsTakeover = false, meta = meta(playerCraftLimit = 3)))
    }

    @Test
    fun `existing takeover reasons still apply`() {
        assertTrue(takesOverCraft(needsTakeover = true, meta = meta()))
        assertTrue(takesOverCraft(needsTakeover = false, meta = meta(supportCrafter = true)))
    }

    @Test
    fun `no-result recipes never take over`() {
        assertFalse(takesOverCraft(needsTakeover = true, meta = meta(giveResultItem = false, playerCraftLimit = 3)))
    }
}
