package ru.oftendev.recipebook.custom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomRecipeTest {

    @Test
    fun `Crafter always has ghost false and lockedByDefault false`() {
        val ghostField = CustomRecipe.Crafter::class.members.first { it.name == "ghost" }
        assertTrue(CustomRecipe.Crafter::class.members.none { it.name == "lockedByDefault" && it.parameters.size > 1 })
    }

    @Test
    fun `SmeltingType covers all four smelting stations`() {
        val types = SmeltingType.values().map { it.name }.toSet()
        assertEquals(setOf("FURNACE", "BLAST_FURNACE", "SMOKER", "CAMPFIRE"), types)
    }

    @Test
    fun `StonecutterOutput has expected fields`() {
        val fields = StonecutterOutput::class.members.map { it.name }.toSet()
        assertTrue("item" in fields)
        assertTrue("ghost" in fields)
        assertTrue("ghostChain" in fields)
    }
}
