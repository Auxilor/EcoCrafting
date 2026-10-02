package com.exanthiax.ecocrafting.crafting.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CraftLimitFixesTest {

    @Test
    fun `taken-over crafting table crafts deliver the output amount for every craft`() {
        assertEquals(4, craftedItemAmount(outputAmount = 4, crafts = 1))
        assertEquals(32, craftedItemAmount(outputAmount = 4, crafts = 8))
        assertEquals(3, craftedItemAmount(outputAmount = 1, crafts = 3))
    }

    @Test
    fun `clicking an empty merchant result slot is ignored`() {
        assertEquals(MerchantClick.IGNORE, classifyMerchantClick(resultPresent = false, shiftClick = false, hasCraftLimit = true))
        assertEquals(MerchantClick.IGNORE, classifyMerchantClick(resultPresent = false, shiftClick = true, hasCraftLimit = false))
    }

    @Test
    fun `shift-clicking a limited trade is blocked so vanilla cannot repeat it past the limit`() {
        assertEquals(MerchantClick.BLOCK_SHIFT, classifyMerchantClick(resultPresent = true, shiftClick = true, hasCraftLimit = true))
    }

    @Test
    fun `unlimited trades and single clicks proceed as before`() {
        assertEquals(MerchantClick.PROCEED, classifyMerchantClick(resultPresent = true, shiftClick = true, hasCraftLimit = false))
        assertEquals(MerchantClick.PROCEED, classifyMerchantClick(resultPresent = true, shiftClick = false, hasCraftLimit = true))
    }

    @Test
    fun `a limit notice is sent once per key until cleared`() {
        val throttle = NoticeThrottle<String>()
        assertTrue(throttle.shouldNotify("furnace"))
        assertFalse(throttle.shouldNotify("furnace"))
        assertTrue(throttle.shouldNotify("campfire"))
        throttle.clear("furnace")
        assertTrue(throttle.shouldNotify("furnace"))
    }
}
