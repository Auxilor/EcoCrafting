package com.exanthiax.ecocrafting.recipe.service

import com.willfp.eco.core.items.TestableItem
import com.willfp.eco.core.recipe.parts.GroupedTestableItems
import com.exanthiax.ecocrafting.recipe.model.RecipeIngredient
import org.bukkit.inventory.ItemStack

// Small helpers shared by every "external recipe -> ResolvedRecipe" mapping (eco's own
// CraftingRecipe system, eco's WorkstationRecipes, and plain Bukkit recipes) - split out
// so none of those three mapping files need to depend on each other.

internal fun TestableItem.allDisplayItems(): List<ItemStack> =
    if (this is GroupedTestableItems && children.isNotEmpty()) children.map { it.item.clone() }
    else listOf(item.clone())

internal fun List<RecipeIngredient>.normalizeToNine(air: ItemStack): List<RecipeIngredient> {
    if (size == 9) return this
    return take(9) + List((9 - size).coerceAtLeast(0)) { RecipeIngredient.empty(air) }
}
