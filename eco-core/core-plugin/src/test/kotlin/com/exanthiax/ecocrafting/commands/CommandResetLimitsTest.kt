package com.exanthiax.ecocrafting.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommandResetLimitsTest {

    @Test
    fun `the ecocrafting namespace is stripped from a recipe argument`() {
        assertEquals("foo", recipeIdArgument("ecocrafting:foo"))
        assertEquals("foo", recipeIdArgument("foo"))
        assertEquals("other:foo", recipeIdArgument("other:foo"))
    }
}
