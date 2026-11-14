package com.exanthiax.ecocrafting.recipe.service

import com.exanthiax.ecocrafting.recipe.model.RecipeIngredient
import org.bukkit.inventory.ItemStack
import wiki.ecohub.game.item.lookup.GroupedTestableItems
import wiki.ecohub.game.item.lookup.TestableItem

// Small helpers shared by every "external recipe -> ResolvedRecipe" mapping (eco's own
// CraftingRecipe system, eco's WorkstationRecipes, and plain Bukkit recipes) - split out
// so none of those three mapping files need to depend on each other.

internal fun TestableItem.allDisplayItems(): List<ItemStack> =
    if (this is GroupedTestableItems && getChildren().isNotEmpty()) getChildren().map { it.getItem().clone() }
    else listOf(getItem().clone())

internal fun List<RecipeIngredient>.normalizeToNine(air: ItemStack): List<RecipeIngredient> {
    if (size == 9) return this
    return take(9) + List((9 - size).coerceAtLeast(0)) { RecipeIngredient.empty(air) }
}
