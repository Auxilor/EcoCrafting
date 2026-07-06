package io.auxilor.ecocrafting.custom

import org.bukkit.inventory.ItemStack
import kotlin.test.Test
import kotlin.test.assertEquals

// Only null/empty-input cases are covered: constructing a real ItemStack here throws
// "No RegistryAccess implementation found" (needs a live server; no Bukkit mock lib
// installed in this project), so the occupied-slot/minimum logic is untested.
class CraftAmountTest {

    @Test
    fun `maxCraftsFromGrid returns MAX_VALUE for an empty matrix`() {
        val matrix: Array<ItemStack?> = arrayOf(null, null, null)
        assertEquals(Int.MAX_VALUE, maxCraftsFromGrid(matrix))
    }

    @Test
    fun `maxCraftsFromInput returns 0 for a null stack`() {
        assertEquals(0, maxCraftsFromInput(null))
    }
}
