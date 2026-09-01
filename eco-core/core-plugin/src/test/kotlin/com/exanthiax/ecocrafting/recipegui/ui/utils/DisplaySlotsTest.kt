package com.exanthiax.ecocrafting.recipegui.ui.utils

import com.willfp.eco.core.items.CustomItem
import com.willfp.eco.core.items.Items
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.bukkit.NamespacedKey
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.bukkit.inventory.ItemStack

class DisplaySlotsTest {

    private val uncut: ItemStack = mockk()
    private val flawed: ItemStack = mockk()

    private fun custom(id: String): CustomItem = mockk {
        every { key } returns NamespacedKey("ecoitems", id)
    }

    @BeforeEach
    fun setUp() {
        mockkStatic(Items::class)
    }

    @Test
    fun `two custom items sharing a base material are not the same item`() {
        // Both are player_head-backed EcoItems - comparing Material alone would call
        // them identical and swallow the click-through to the ingredient's own recipe.
        every { Items.getCustomItem(uncut) } returns custom("uncut_topaz")
        every { Items.getCustomItem(flawed) } returns custom("flawed_topaz")

        assertFalse(isSameItemAs(uncut, flawed))
    }

    @Test
    fun `the same custom item is the same item`() {
        every { Items.getCustomItem(uncut) } returns custom("uncut_topaz")
        every { Items.getCustomItem(flawed) } returns custom("uncut_topaz")

        assertTrue(isSameItemAs(uncut, flawed))
    }

    @Test
    fun `a custom item is never the same as a plain vanilla item`() {
        every { Items.getCustomItem(uncut) } returns custom("uncut_topaz")
        every { Items.getCustomItem(flawed) } returns null

        assertFalse(isSameItemAs(uncut, flawed))
        assertFalse(isSameItemAs(flawed, uncut))
    }

    @Test
    fun `plain items fall back to a similarity check`() {
        every { Items.getCustomItem(uncut) } returns null
        every { Items.getCustomItem(flawed) } returns null
        every { uncut.isSimilar(flawed) } returns true

        assertTrue(isSameItemAs(uncut, flawed))
    }
}
