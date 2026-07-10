package io.auxilor.ecocrafting.recipe.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecipeSymmetryTest {

    // Every transform must be a permutation of the 3x3 grid (0..8), each index exactly once.
    private fun assertIsPermutation(transform: IntArray) {
        assertEquals((0..8).toSet(), transform.toSet())
    }

    @Test
    fun `every symmetry transform is a valid 3x3 permutation`() {
        assertIsPermutation(RecipeSymmetry.ROT_90_CW)
        assertIsPermutation(RecipeSymmetry.ROT_180)
        assertIsPermutation(RecipeSymmetry.ROT_270_CW)
        assertIsPermutation(RecipeSymmetry.MIRROR_H)
    }

    @Test
    fun `applying a 90 degree rotation four times returns to the original`() {
        var current = (0..8).toList()
        repeat(4) {
            current = RecipeSymmetry.ROT_90_CW.map { current[it] }
        }
        assertEquals((0..8).toList(), current)
    }

    @Test
    fun `180 degree rotation is its own inverse`() {
        val grid = (0..8).toList()
        val rotatedTwice = RecipeSymmetry.ROT_180.map { grid[it] }.let { once ->
            RecipeSymmetry.ROT_180.map { once[it] }
        }
        assertEquals(grid, rotatedTwice)
    }

    @Test
    fun `mirroring twice returns to the original`() {
        val grid = (0..8).toList()
        val mirroredTwice = RecipeSymmetry.MIRROR_H.map { grid[it] }.let { once ->
            RecipeSymmetry.MIRROR_H.map { once[it] }
        }
        assertEquals(grid, mirroredTwice)
    }

    @Test
    fun `center cell is fixed under every transform`() {
        // Index 4 is the centre of a 3x3 grid - every rotation/mirror maps it to itself.
        assertTrue(RecipeSymmetry.ROT_90_CW[4] == 4)
        assertTrue(RecipeSymmetry.ROT_180[4] == 4)
        assertTrue(RecipeSymmetry.ROT_270_CW[4] == 4)
        assertTrue(RecipeSymmetry.MIRROR_H[4] == 4)
    }
}
