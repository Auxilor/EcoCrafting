package com.exanthiax.ecocrafting.crafting.integration

import com.willfp.eco.core.items.TestableItem
import com.willfp.eco.core.recipe.parts.TestableStack
import com.willfp.eco.core.recipe.workstation.CrafterRecipe
import io.mockk.every
import io.mockk.mockk
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GridRequirementsTest {

    // A part that matches only the stacks it was told about, so a grid can hold two
    // different items without either standing in for the other.
    private fun part(vararg matching: ItemStack): TestableItem = mockk {
        every { matches(any()) } answers { firstArg<ItemStack?>() in matching }
    }

    private fun stack(amount: Int): ItemStack = mockk {
        every { isEmpty } returns false
        every { this@mockk.amount } returns amount
    }

    private val empty: ItemStack? = null

    @Suppress("UNCHECKED_CAST")
    private fun recipe(parts: List<TestableItem?>, shapeless: Boolean): CrafterRecipe = mockk {
        every { isShapeless } returns shapeless
        every { this@mockk.parts } returns (parts as List<TestableItem>)
    }

    @Test
    fun `a shaped recipe reports the required amount of every filled slot`() {
        val topaz = stack(8)
        val stone = stack(1)
        val parts = listOf(
            TestableStack(part(topaz), 4), part(stone), null, null, null, null, null, null, null
        )
        val matrix = arrayOf<ItemStack?>(topaz, stone, empty, empty, empty, empty, empty, empty, empty)

        assertEquals(mapOf(0 to 4, 1 to 1), gridRequirements(recipe(parts, shapeless = false), matrix))
    }

    @Test
    fun `a shaped recipe laid out in the wrong slots reports nothing`() {
        val topaz = stack(8)
        val parts = listOf<TestableItem?>(part(topaz), null, null, null, null, null, null, null, null)
        val matrix = arrayOf<ItemStack?>(empty, topaz, empty, empty, empty, empty, empty, empty, empty)

        assertNull(gridRequirements(recipe(parts, shapeless = false), matrix))
    }

    @Test
    fun `a shaped recipe is still recognised when the grid is rotated`() {
        // Symmetry variants are registered under their own Bukkit key but only the base
        // orientation is kept as a CrafterRecipe, so the grid arrives rotated.
        val topaz = stack(8)
        val parts = listOf<TestableItem?>(TestableStack(part(topaz), 4), null, null, null, null, null, null, null, null)
        // ROT_90_CW maps base slot 0 to grid slot 2.
        val matrix = arrayOf<ItemStack?>(empty, empty, topaz, empty, empty, empty, empty, empty, empty)

        assertEquals(mapOf(2 to 4), gridRequirements(recipe(parts, shapeless = false), matrix))
    }

    @Test
    fun `an under-filled slot still reports its requirement so the caller can refuse`() {
        // The whole point: Bukkit's ExactChoice ignores stack size, so the craft has to be
        // recognised before it can be turned down for having too little in the slot.
        val topaz = stack(1)
        val parts = listOf<TestableItem?>(TestableStack(part(topaz), 4), null, null, null, null, null, null, null, null)
        val matrix = arrayOf<ItemStack?>(topaz, empty, empty, empty, empty, empty, empty, empty, empty)

        assertEquals(mapOf(0 to 4), gridRequirements(recipe(parts, shapeless = false), matrix))
    }

    @Test
    fun `a shapeless recipe reports requirements against whichever slots hold its parts`() {
        val topaz = stack(8)
        val parts = listOf<TestableItem?>(TestableStack(part(topaz), 4), TestableStack(part(topaz), 4))
        val matrix = arrayOf<ItemStack?>(empty, topaz, empty, topaz, empty, empty, empty, empty, empty)

        assertEquals(mapOf(1 to 4, 3 to 4), gridRequirements(recipe(parts, shapeless = true), matrix))
    }

    @Test
    fun `a shapeless recipe with the wrong number of items reports nothing`() {
        val topaz = stack(8)
        val parts = listOf<TestableItem?>(TestableStack(part(topaz), 4), TestableStack(part(topaz), 4))
        val matrix = arrayOf<ItemStack?>(topaz, empty, empty, empty, empty, empty, empty, empty, empty)

        assertNull(gridRequirements(recipe(parts, shapeless = true), matrix))
    }

    @Test
    fun `craft count divides each slot by what that slot has to supply`() {
        val matrix = arrayOf<ItemStack?>(stack(9), stack(2), empty, empty, empty, empty, empty, empty, empty)

        assertEquals(2, maxCraftsFromGrid(matrix, mapOf(0 to 4, 1 to 1)))
    }

    @Test
    fun `craft count treats an unlisted slot as needing one`() {
        val matrix = arrayOf<ItemStack?>(stack(9), stack(2), empty, empty, empty, empty, empty, empty, empty)

        assertEquals(2, maxCraftsFromGrid(matrix, emptyMap()))
    }

    @Test
    fun `an empty grid places no limit on the craft count`() {
        val matrix = arrayOf<ItemStack?>(empty, empty, empty, empty, empty, empty, empty, empty, empty)

        assertEquals(Int.MAX_VALUE, maxCraftsFromGrid(matrix, emptyMap()))
    }
}
