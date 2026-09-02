package com.exanthiax.ecocrafting.crafting.integration

import com.willfp.eco.core.recipe.workstation.CrafterRecipe
import io.mockk.every
import io.mockk.mockk
import org.bukkit.NamespacedKey
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class CraftingTableRecipeSelectionTest {

    private fun recipe(id: String): CrafterRecipe = mockk {
        every { key } returns NamespacedKey("ecocrafting", id)
    }

    @Test
    fun `the event's recipe is used when it agrees with the grid`() {
        val fromEvent = recipe("flawed_topaz_craft")
        val fromGrid = recipe("flawed_topaz_craft")

        assertSame(fromEvent, trustedEventRecipe(fromEvent, fromGrid))
    }

    @Test
    fun `the event's recipe is used when the grid matched nothing`() {
        val fromEvent = recipe("flawed_topaz_craft")

        assertSame(fromEvent, trustedEventRecipe(fromEvent, null))
    }

    @Test
    fun `the event's recipe is dropped when the grid says it's a different recipe`() {
        // Every crafting-table recipe registers its Bukkit twin with material-only
        // ingredients, so three player_head recipes collapse onto one Bukkit shape and
        // CraftItemEvent can name any of them.
        val fromEvent = recipe("perfect_topaz_craft")
        val fromGrid = recipe("flawed_topaz_craft")

        assertNull(trustedEventRecipe(fromEvent, fromGrid))
    }

    @Test
    fun `nothing is trusted when the event had no matching recipe`() {
        assertNull(trustedEventRecipe(null, recipe("flawed_topaz_craft")))
    }
}
