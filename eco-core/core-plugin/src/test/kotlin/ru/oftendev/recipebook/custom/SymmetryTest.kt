package ru.oftendev.recipebook.custom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SymmetryTest {

    @Test
    fun `ROT_90_CW remaps index 0 to index 6`() {
        assertEquals(6, RecipeSymmetry.ROT_90_CW[0])
    }

    @Test
    fun `ROT_90_CW remaps index 2 to index 0`() {
        assertEquals(0, RecipeSymmetry.ROT_90_CW[2])
    }

    @Test
    fun `ROT_180 remaps index 0 to index 8`() {
        assertEquals(8, RecipeSymmetry.ROT_180[0])
    }

    @Test
    fun `MIRROR_H remaps index 0 to index 2`() {
        assertEquals(2, RecipeSymmetry.MIRROR_H[0])
    }

    @Test
    fun `all rotation arrays have exactly 9 elements`() {
        assertTrue(RecipeSymmetry.ROT_90_CW.size == 9)
        assertTrue(RecipeSymmetry.ROT_180.size == 9)
        assertTrue(RecipeSymmetry.ROT_270_CW.size == 9)
        assertTrue(RecipeSymmetry.MIRROR_H.size == 9)
    }
}
