package com.exanthiax.ecocrafting.recipegui.ui

import com.exanthiax.ecocrafting.recipe.model.RecipeDisplayType

enum class WorkstationMarkerState { ACTIVE, INACTIVE, NOT_APPLICABLE }

val WORKSTATION_MARKERS: Map<Char, Set<RecipeDisplayType>> = mapOf(
    'C' to setOf(RecipeDisplayType.CRAFTING, RecipeDisplayType.CRAFTER),
    'F' to setOf(RecipeDisplayType.SMELTING),
    'B' to setOf(RecipeDisplayType.BLAST_FURNACE),
    'S' to setOf(RecipeDisplayType.SMOKER),
    'P' to setOf(RecipeDisplayType.CAMPFIRE),
    'M' to setOf(RecipeDisplayType.SMITHING),
    'T' to setOf(RecipeDisplayType.STONECUTTER),
    'W' to setOf(RecipeDisplayType.BREWING),
    'G' to setOf(RecipeDisplayType.GRINDSTONE),
    'A' to setOf(RecipeDisplayType.ANVIL),
    'V' to setOf(RecipeDisplayType.VILLAGER)
)

val MARKER_CONFIG_KEY: Map<Char, String> = mapOf(
    'C' to "crafting_table",
    'F' to "furnace",
    'B' to "blast_furnace",
    'S' to "smoker",
    'P' to "campfire",
    'M' to "smithing_table",
    'T' to "stonecutter",
    'W' to "brewing_stand",
    'G' to "grindstone",
    'A' to "anvil",
    'V' to "villager"
)

fun workstationMarkerState(
    markerTypes: Set<RecipeDisplayType>,
    currentType: RecipeDisplayType?,
    availableTypes: Set<RecipeDisplayType>
): WorkstationMarkerState = when {
    currentType != null && markerTypes.contains(currentType) -> WorkstationMarkerState.ACTIVE
    markerTypes.any { availableTypes.contains(it) } -> WorkstationMarkerState.INACTIVE
    else -> WorkstationMarkerState.NOT_APPLICABLE
}
