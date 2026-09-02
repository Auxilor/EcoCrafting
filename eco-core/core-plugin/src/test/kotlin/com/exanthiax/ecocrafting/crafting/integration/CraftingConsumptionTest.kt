package com.exanthiax.ecocrafting.crafting.integration

import io.mockk.every
import io.mockk.mockk
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CraftingConsumptionTest {

    private fun stackOf(amount: Int): ItemStack = mockk {
        every { this@mockk.amount } returns amount
    }

    @Test
    fun `a single-item input yields one craft per item`() {
        assertEquals(7, maxCraftsFromInput(stackOf(7), 1))
    }

    @Test
    fun `a multi-item input divides the available amount by the required amount`() {
        assertEquals(2, maxCraftsFromInput(stackOf(9), 4))
    }

    @Test
    fun `an input below the required amount yields no crafts`() {
        assertEquals(0, maxCraftsFromInput(stackOf(3), 4))
    }

    @Test
    fun `a missing input yields no crafts`() {
        assertEquals(0, maxCraftsFromInput(null, 4))
    }
}
