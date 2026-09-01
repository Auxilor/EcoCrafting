package com.exanthiax.ecocrafting.recipe.model

import com.willfp.eco.core.items.TestableItem
import com.willfp.eco.core.recipe.parts.TestableStack
import io.mockk.every
import io.mockk.mockk
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecipeIngredientTest {

    private val stack: ItemStack = mockk { every { amount } returns 1 }

    private fun handle(matches: Boolean): TestableItem = mockk {
        every { this@mockk.matches(any()) } returns matches
    }

    @Test
    fun `a plain testable item requires one of the item`() {
        assertEquals(1, handle(true).requiredAmount())
    }

    @Test
    fun `a testable stack reports its required amount`() {
        assertEquals(4, TestableStack(handle(true), 4).requiredAmount())
    }

    @Test
    fun `matching a stack ignoring amount skips the testable stack's size requirement`() {
        assertFalse(TestableStack(handle(true), 4).matches(stack))
        assertTrue(TestableStack(handle(true), 4).matchesIgnoringAmount(stack))
    }

    @Test
    fun `matching ignoring amount still defers to the underlying item test`() {
        assertFalse(TestableStack(handle(false), 4).matchesIgnoringAmount(stack))
    }

    @Test
    fun `matching ignoring amount is a plain match for a non-stack item`() {
        assertTrue(handle(true).matchesIgnoringAmount(stack))
        assertFalse(handle(false).matchesIgnoringAmount(stack))
    }
}
