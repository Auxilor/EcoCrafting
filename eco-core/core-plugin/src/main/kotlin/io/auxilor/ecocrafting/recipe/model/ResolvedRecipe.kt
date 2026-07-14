package io.auxilor.ecocrafting.recipe.model

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack

// Ingredients are always normalized to a 3x3 grid for GUI display.
data class ResolvedRecipe(
    val key: NamespacedKey?,
    val output: ItemStack,
    val ingredients: List<RecipeIngredient>,
    val permission: String? = null,
    val source: RecipeSource = RecipeSource.UNKNOWN,
    val shapeless: Boolean = false,
    val displayType: RecipeDisplayType = RecipeDisplayType.CRAFTING,
    val locked: Boolean = false,
    val cookTime: Int? = null,
    val brewTime: Int? = null,
    val villagerXp: Int? = null
) {
    val displayItems: List<ItemStack>
        get() = ingredients.map { it.displayItem.clone() }
}
