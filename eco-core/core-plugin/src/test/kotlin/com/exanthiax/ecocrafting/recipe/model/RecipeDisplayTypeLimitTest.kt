package com.exanthiax.ecocrafting.recipe.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecipeDisplayTypeLimitTest {

    @Test
    fun `brewing does not support craft limits`() {
        assertFalse(RecipeDisplayType.BREWING.supportsCraftLimits)
    }

    @Test
    fun `every other workstation supports craft limits`() {
        RecipeDisplayType.entries
            .filter { it != RecipeDisplayType.BREWING }
            .forEach { assertTrue(it.supportsCraftLimits, it.name) }
    }
}
