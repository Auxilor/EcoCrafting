package com.exanthiax.ecocrafting.recipegui.integration

import com.willfp.eco.core.items.CustomItem
import com.willfp.eco.core.items.Items
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RecipeCreatorConfigWriterTest {

    private val item: ItemStack = mockk {
        every { isEmpty } returns false
    }

    @BeforeEach
    fun setUp() {
        mockkStatic(Items::class)
    }

    private fun asCustomItem(id: String) {
        every { Items.getCustomItem(item) } returns mockk<CustomItem> {
            every { key } returns NamespacedKey("ecoitems", id)
        }
    }

    @Test
    fun `a custom item serializes to its key alone`() {
        // eco's own toLookupString would append `texture:... name:"..."` here, which
        // becomes an exact-match predicate the EcoItem stops satisfying the moment its
        // texture or display name is edited.
        asCustomItem("uncut_topaz")
        every { item.amount } returns 1

        assertEquals("ecoitems:uncut_topaz", itemLookupString(item))
    }

    @Test
    fun `a stacked custom item keeps its amount`() {
        asCustomItem("uncut_topaz")
        every { item.amount } returns 4

        assertEquals("ecoitems:uncut_topaz 4", itemLookupString(item))
    }

    @Test
    fun `a vanilla item still round-trips through eco's own serialization`() {
        every { Items.getCustomItem(item) } returns null
        every { Items.toLookupString(item) } returns "player_head texture:abc"

        assertEquals("player_head texture:abc", itemLookupString(item))
    }

    @Test
    fun `an empty or missing item serializes to air`() {
        assertEquals("air", itemLookupString(mockk<ItemStack> { every { isEmpty } returns true }))
        assertEquals("air", itemLookupString(null))
    }
}
