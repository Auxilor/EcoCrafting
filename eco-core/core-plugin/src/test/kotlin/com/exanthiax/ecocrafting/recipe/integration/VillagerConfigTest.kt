package com.exanthiax.ecocrafting.recipe.integration

import org.bukkit.Keyed
import org.bukkit.NamespacedKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VillagerConfigTest {

    private val farmer = Keyed { NamespacedKey.minecraft("farmer") }

    private val professions = listOf(farmer, Keyed { NamespacedKey.minecraft("librarian") })

    @Test
    fun `uppercase profession resolves`() {
        assertSame(farmer, parseVillagerProfession("FARMER", professions))
    }

    @Test
    fun `missing or blank profession means any profession`() {
        assertNull(parseVillagerProfession(null, professions))
        assertNull(parseVillagerProfession("", professions))
    }

    @Test
    fun `misspelled profession fails and lists valid professions`() {
        val exception = assertThrows<IllegalStateException> { parseVillagerProfession("FARMR", professions) }
        assertTrue(exception.message!!.startsWith("Unknown villager profession 'FARMR'"))
        assertTrue(exception.message!!.contains("FARMER"))
    }

    @Test
    fun `missing chance defaults to always`() {
        assertEquals(1.0, parseVillagerChance(null))
    }

    @Test
    fun `chance is a percentage clamped between 0 and 100`() {
        assertEquals(0.5, parseVillagerChance("50"))
        assertEquals(1.0, parseVillagerChance("150"))
        assertEquals(0.0, parseVillagerChance("-1"))
    }

    @Test
    fun `non-numeric chance fails`() {
        val exception = assertThrows<IllegalStateException> { parseVillagerChance("50%") }
        assertEquals("chance must be a number between 0 and 100, got '50%'", exception.message)
    }
}
