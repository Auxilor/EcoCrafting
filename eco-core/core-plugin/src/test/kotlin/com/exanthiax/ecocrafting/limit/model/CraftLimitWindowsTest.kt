package com.exanthiax.ecocrafting.limit.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CraftLimitWindowsTest {

    @Test
    fun `player counts round trip through serialise and parse`() {
        val count = PlayerCraftCount("ecocrafting:epic_sword", 1000L, 3)
        assertEquals(listOf(count), parsePlayerCraftCounts(listOf(count.serialise())))
    }

    @Test
    fun `malformed player entries are skipped`() {
        val parsed = parsePlayerCraftCounts(
            listOf("ecocrafting:a;1000;2", "garbage", "ecocrafting:b;notanumber;1", "ecocrafting:c;5;x", "")
        )
        assertEquals(listOf(PlayerCraftCount("ecocrafting:a", 1000L, 2)), parsed)
    }

    @Test
    fun `recipe values round trip and skip malformed entries`() {
        val values = mapOf("ecocrafting:a" to 4L, ALL_RECIPES to 99L)
        assertEquals(values, parseRecipeValues(values.serialiseRecipeValues() + "broken" + "x;y"))
    }

    @Test
    fun `reset stamp is the later of recipe and all-recipes stamps`() {
        val resets = mapOf("ecocrafting:a" to 50L, ALL_RECIPES to 80L)
        assertEquals(80L, resetStampFor(resets, "ecocrafting:a"))
        assertEquals(80L, resetStampFor(resets, "ecocrafting:b"))
        assertEquals(Long.MIN_VALUE, resetStampFor(emptyMap(), "ecocrafting:a"))
    }

    @Test
    fun `count is current only when started after the reset stamp`() {
        val resets = mapOf("ecocrafting:a" to 100L)
        assertTrue(PlayerCraftCount("ecocrafting:a", 101L, 1).isCurrent(resets))
        assertFalse(PlayerCraftCount("ecocrafting:a", 100L, 1).isCurrent(resets))
        assertFalse(PlayerCraftCount("ecocrafting:a", 50L, 1).isCurrent(mapOf(ALL_RECIPES to 60L)))
    }

    @Test
    fun `stale counts read as zero`() {
        val counts = listOf(PlayerCraftCount("ecocrafting:a", 10L, 7))
        assertEquals(7, currentPlayerCrafts(counts, "ecocrafting:a", emptyMap()))
        assertEquals(0, currentPlayerCrafts(counts, "ecocrafting:a", mapOf(ALL_RECIPES to 20L)))
        assertEquals(0, currentPlayerCrafts(counts, "ecocrafting:b", emptyMap()))
    }

    @Test
    fun `window started in the same millisecond as a reset still counts`() {
        val resets = mapOf("ecocrafting:a" to 500L)
        assertEquals(501L, windowStart(500L, resets, "ecocrafting:a"))
        val recorded = recordPlayerCrafts(emptyList(), "ecocrafting:a", 2, 500L, resets)
        assertEquals(2, currentPlayerCrafts(recorded, "ecocrafting:a", resets))
    }

    @Test
    fun `recording adds to a current window and keeps its start`() {
        val counts = listOf(PlayerCraftCount("ecocrafting:a", 10L, 3), PlayerCraftCount("ecocrafting:b", 10L, 1))
        val recorded = recordPlayerCrafts(counts, "ecocrafting:a", 2, 999L, emptyMap())
        assertEquals(PlayerCraftCount("ecocrafting:a", 10L, 5), recorded.first { it.recipe == "ecocrafting:a" })
        assertEquals(PlayerCraftCount("ecocrafting:b", 10L, 1), recorded.first { it.recipe == "ecocrafting:b" })
    }

    @Test
    fun `recording after a reset starts a new window`() {
        val counts = listOf(PlayerCraftCount("ecocrafting:a", 10L, 9))
        val recorded = recordPlayerCrafts(counts, "ecocrafting:a", 1, 999L, mapOf(ALL_RECIPES to 20L))
        assertEquals(listOf(PlayerCraftCount("ecocrafting:a", 999L, 1)), recorded)
    }

    @Test
    fun `negative and absent limits normalise to unlimited`() {
        assertEquals(UNLIMITED, normaliseLimit(null))
        assertEquals(UNLIMITED, normaliseLimit(-1))
        assertEquals(UNLIMITED, normaliseLimit(-5))
        assertEquals(0, normaliseLimit(0))
        assertEquals(12, normaliseLimit(12))
    }

    @Test
    fun `remaining is unlimited, floored at zero, or limit minus used`() {
        assertEquals(Int.MAX_VALUE, remainingUnder(UNLIMITED, 50))
        assertEquals(0, remainingUnder(0, 0))
        assertEquals(0, remainingUnder(5, 9))
        assertEquals(2, remainingUnder(5, 3))
    }

    @Test
    fun `negative stored counts are treated as malformed`() {
        assertEquals(emptyList<PlayerCraftCount>(), parsePlayerCraftCounts(listOf("ecocrafting:a;10;-5")))
    }

    @Test
    fun `a new reset stamp always lands after every earlier stamp`() {
        val resets = mapOf("ecocrafting:a" to 500L, ALL_RECIPES to 300L)
        assertEquals(501L, nextResetStamp(500L, resets, "ecocrafting:a"))
        assertEquals(900L, nextResetStamp(900L, resets, "ecocrafting:a"))
        assertEquals(501L, nextResetStamp(400L, resets, ALL_RECIPES))
        assertEquals(400L, nextResetStamp(400L, emptyMap(), ALL_RECIPES))
    }

    @Test
    fun `a craft between two resets in the same millisecond is cleared by the second`() {
        val first = mapOf(ALL_RECIPES to nextResetStamp(500L, emptyMap(), ALL_RECIPES))
        val counts = recordPlayerCrafts(emptyList(), "ecocrafting:a", 1, 500L, first)
        val second = first + (ALL_RECIPES to nextResetStamp(500L, first, ALL_RECIPES))
        assertEquals(0, currentPlayerCrafts(counts, "ecocrafting:a", second))
    }
}
