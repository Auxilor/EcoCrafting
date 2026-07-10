package io.auxilor.ecocrafting.recipe.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RecipeCapEnforcerTest {

    private lateinit var enforcer: RecipeCapEnforcer

    @BeforeEach
    fun setUp() {
        enforcer = RecipeCapEnforcer()
    }

    @Test
    fun `crafting_table cap is 10, every other type is 5`() {
        assertEquals(10, enforcer.capFor("crafting_table"))
        assertEquals(5, enforcer.capFor("furnace"))
        assertEquals(5, enforcer.capFor("anvil"))
    }

    @Test
    fun `paid version never enforces a cap`() {
        repeat(20) { i -> assertTrue(enforcer.tryRegister(freeVersion = false, type = "furnace", id = "r$i")) }
    }

    @Test
    fun `free version rejects registration once a type's cap is reached`() {
        repeat(5) { i -> assertTrue(enforcer.tryRegister(freeVersion = true, type = "furnace", id = "furnace-$i")) }
        assertFalse(enforcer.tryRegister(freeVersion = true, type = "furnace", id = "furnace-overflow"))
    }

    @Test
    fun `caps are tracked independently per recipe type`() {
        repeat(5) { i -> enforcer.tryRegister(freeVersion = true, type = "furnace", id = "furnace-$i") }
        // furnace is full, but crafting_table (cap 10) is a separate bucket and still has room.
        assertTrue(enforcer.tryRegister(freeVersion = true, type = "crafting_table", id = "ct-1"))
    }

    @Test
    fun `re-registering an already-counted id on reload does not consume another slot`() {
        repeat(5) { i -> enforcer.tryRegister(freeVersion = true, type = "furnace", id = "furnace-$i") }
        // Same ids re-submitted (e.g. on /ecocrafting reload) must not push the type over its cap.
        assertTrue(enforcer.tryRegister(freeVersion = true, type = "furnace", id = "furnace-0"))
    }

    @Test
    fun `clear resets all per-type counters`() {
        repeat(5) { i -> enforcer.tryRegister(freeVersion = true, type = "furnace", id = "furnace-$i") }
        enforcer.clear()
        assertTrue(enforcer.tryRegister(freeVersion = true, type = "furnace", id = "furnace-new"))
    }
}
