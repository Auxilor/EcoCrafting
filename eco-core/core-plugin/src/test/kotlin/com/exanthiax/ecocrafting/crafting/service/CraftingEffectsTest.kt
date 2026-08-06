package com.exanthiax.ecocrafting.crafting.service

import com.willfp.eco.core.price.ConfiguredPrice
import io.mockk.every
import io.mockk.mockk
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CraftingEffectsTest {

    private val player: Player = mockk()

    @Test
    fun `desired amount is returned outright when it's affordable`() {
        val price = mockk<ConfiguredPrice>()
        every { price.canAfford(player, 5.0) } returns true

        assertEquals(5, priceAffordableAmount(player, price, 5))
    }

    @Test
    fun `binary search finds the largest affordable amount`() {
        val price = mockk<ConfiguredPrice>()
        // Player can afford up to (and including) 7 units, but not 8+.
        every { price.canAfford(player, any()) } answers { (it.invocation.args[1] as Double) <= 7.0 }

        assertEquals(7, priceAffordableAmount(player, price, 10))
    }

    @Test
    fun `zero is returned when nothing at all is affordable`() {
        val price = mockk<ConfiguredPrice>()
        every { price.canAfford(player, any()) } returns false

        assertEquals(0, priceAffordableAmount(player, price, 10))
    }

    @Test
    fun `non-positive desired amount short-circuits to zero`() {
        val price = mockk<ConfiguredPrice>()
        assertEquals(0, priceAffordableAmount(player, price, 0))
        assertEquals(0, priceAffordableAmount(player, price, -3))
    }
}
