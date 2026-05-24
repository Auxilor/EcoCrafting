package ru.oftendev.recipebook.recipe

import kotlin.test.Test
import kotlin.test.assertEquals

class VanillaRecipeScannerTest {

    @Test
    fun `formatName converts underscore-separated name to title case`() {
        assertEquals("Diamond Sword", VanillaRecipeScanner.formatName("DIAMOND_SWORD"))
    }

    @Test
    fun `formatName handles multi-word name`() {
        assertEquals("Oak Log", VanillaRecipeScanner.formatName("OAK_LOG"))
    }

    @Test
    fun `formatName handles single word`() {
        assertEquals("Stone", VanillaRecipeScanner.formatName("STONE"))
    }

    @Test
    fun `formatName handles already lowercase`() {
        assertEquals("Iron Ingot", VanillaRecipeScanner.formatName("iron_ingot"))
    }
}
