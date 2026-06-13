package io.auxilor.ecocrafting.gui

import io.auxilor.ecocrafting.recipe.RecipeDisplayType
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkstationMarkerStateTest {

    private val craftingGroup = setOf(RecipeDisplayType.CRAFTING, RecipeDisplayType.CRAFTER)
    private val smeltingGroup = setOf(RecipeDisplayType.SMELTING)

    @Test
    fun `returns ACTIVE when currentType is in markerTypes`() {
        val result = workstationMarkerState(
            markerTypes = craftingGroup,
            currentType = RecipeDisplayType.CRAFTING,
            availableTypes = setOf(RecipeDisplayType.CRAFTING, RecipeDisplayType.SMELTING)
        )
        assertEquals(WorkstationMarkerState.ACTIVE, result)
    }

    @Test
    fun `returns ACTIVE when currentType is CRAFTER and marker is crafting group`() {
        val result = workstationMarkerState(
            markerTypes = craftingGroup,
            currentType = RecipeDisplayType.CRAFTER,
            availableTypes = setOf(RecipeDisplayType.CRAFTER)
        )
        assertEquals(WorkstationMarkerState.ACTIVE, result)
    }

    @Test
    fun `returns INACTIVE when markerType present in available but not current`() {
        val result = workstationMarkerState(
            markerTypes = smeltingGroup,
            currentType = RecipeDisplayType.CRAFTING,
            availableTypes = setOf(RecipeDisplayType.CRAFTING, RecipeDisplayType.SMELTING)
        )
        assertEquals(WorkstationMarkerState.INACTIVE, result)
    }

    @Test
    fun `returns NOT_APPLICABLE when markerType absent from available`() {
        val result = workstationMarkerState(
            markerTypes = smeltingGroup,
            currentType = RecipeDisplayType.CRAFTING,
            availableTypes = setOf(RecipeDisplayType.CRAFTING)
        )
        assertEquals(WorkstationMarkerState.NOT_APPLICABLE, result)
    }

    @Test
    fun `returns NOT_APPLICABLE when currentType is null and markerType absent`() {
        val result = workstationMarkerState(
            markerTypes = smeltingGroup,
            currentType = null,
            availableTypes = emptySet()
        )
        assertEquals(WorkstationMarkerState.NOT_APPLICABLE, result)
    }

    @Test
    fun `returns INACTIVE when currentType is null but markerType present in available`() {
        val result = workstationMarkerState(
            markerTypes = smeltingGroup,
            currentType = null,
            availableTypes = setOf(RecipeDisplayType.SMELTING)
        )
        assertEquals(WorkstationMarkerState.INACTIVE, result)
    }
}
