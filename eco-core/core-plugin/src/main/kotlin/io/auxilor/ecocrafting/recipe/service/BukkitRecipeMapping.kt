package io.auxilor.ecocrafting.recipe.service

import io.auxilor.ecocrafting.recipe.model.IngredientMatcher
import io.auxilor.ecocrafting.recipe.model.RecipeDisplayType
import io.auxilor.ecocrafting.recipe.model.RecipeIngredient
import io.auxilor.ecocrafting.recipe.model.RecipeSource
import io.auxilor.ecocrafting.recipe.model.ResolvedRecipe
import org.bukkit.Bukkit
import org.bukkit.inventory.BlastingRecipe
import org.bukkit.inventory.CampfireRecipe
import org.bukkit.inventory.CookingRecipe
import org.bukkit.inventory.FurnaceRecipe
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe
import org.bukkit.inventory.SmithingTransformRecipe
import org.bukkit.inventory.SmokingRecipe
import org.bukkit.inventory.StonecuttingRecipe as BukkitStonecuttingRecipe

// Maps plain vanilla/other-plugin Bukkit recipes (i.e. anything not registered through
// eco or EcoCrafting) into ResolvedRecipe - the last-resort fallback in resolution order.

internal fun findBukkitRecipe(stack: ItemStack, air: ItemStack, strict: Boolean): ResolvedRecipe? {
    return Bukkit.getRecipesFor(stack).firstNotNullOfOrNull { recipe ->
        val resolved = recipe.toResolvedRecipeOrNull(air) ?: return@firstNotNullOfOrNull null
        if (strict && !resolved.output.isSimilar(stack)) null else resolved
    }
}

internal fun findAllBukkitRecipes(stack: ItemStack, air: ItemStack, strict: Boolean): List<ResolvedRecipe> {
    return Bukkit.getRecipesFor(stack).mapNotNull { recipe ->
        val resolved = recipe.toResolvedRecipeOrNull(air) ?: return@mapNotNull null
        if (strict && !resolved.output.isSimilar(stack)) null else resolved
    }
}

private fun org.bukkit.inventory.Recipe.toResolvedRecipeOrNull(air: ItemStack): ResolvedRecipe? = when (this) {
    is ShapedRecipe             -> toResolvedRecipe(air)
    is ShapelessRecipe          -> toResolvedRecipe(air)
    is CookingRecipe<*>         -> toResolvedRecipe(air)
    is BukkitStonecuttingRecipe -> toResolvedRecipe(air)
    is SmithingTransformRecipe  -> toResolvedRecipe(air)
    else                        -> null
}

private fun ShapedRecipe.toResolvedRecipe(air: ItemStack): ResolvedRecipe {
    val ingredientGrid = MutableList(9) { RecipeIngredient.empty(air) }
    val shape = this.shape
    for (row in shape.indices) {
        if (row >= 3) break
        val line = shape[row]
        for (col in 0 until line.length.coerceAtMost(3)) {
            val choice = choiceMap[line[col]] ?: continue
            ingredientGrid[row * 3 + col] = choice.toIngredient(air)
        }
    }

    return ResolvedRecipe(
        key = key,
        output = result.clone(),
        ingredients = ingredientGrid,
        source = RecipeSource.BUKKIT
    )
}

private fun ShapelessRecipe.toResolvedRecipe(air: ItemStack): ResolvedRecipe {
    val ingredients = choiceList.map { it.toIngredient(air) }.normalizeToNine(air)
    return ResolvedRecipe(
        key = key,
        output = result.clone(),
        ingredients = ingredients,
        source = RecipeSource.BUKKIT,
        shapeless = true
    )
}

private fun RecipeChoice.toIngredient(air: ItemStack): RecipeIngredient {
    val allItems = when (this) {
        is RecipeChoice.ExactChoice -> choices.map { it.clone() }
        is RecipeChoice.MaterialChoice -> choices.map { ItemStack(it) }
        else -> listOf(itemStack?.clone() ?: air.clone())
    }
    val display = allItems.firstOrNull() ?: air.clone()
    val matcher = when (this) {
        is RecipeChoice.ExactChoice -> IngredientMatcher.SimilarItem(display)
        is RecipeChoice.MaterialChoice -> IngredientMatcher.MaterialOnly(display)
        else -> IngredientMatcher.SimilarItem(display)
    }
    return RecipeIngredient(
        displayItem = display,
        matcher = matcher,
        displayAlternatives = if (allItems.size > 1) allItems else emptyList()
    )
}

private fun CookingRecipe<*>.toResolvedRecipe(air: ItemStack): ResolvedRecipe {
    val displayType = when (this) {
        is FurnaceRecipe  -> RecipeDisplayType.SMELTING
        is BlastingRecipe -> RecipeDisplayType.BLAST_FURNACE
        is SmokingRecipe  -> RecipeDisplayType.SMOKER
        is CampfireRecipe -> RecipeDisplayType.CAMPFIRE
        else              -> RecipeDisplayType.SMELTING
    }
    val ingredient = inputChoice.toIngredient(air)
    return ResolvedRecipe(
        key = key,
        output = result.clone(),
        ingredients = listOf(ingredient) + List(8) { RecipeIngredient.empty(air) },
        source = RecipeSource.BUKKIT,
        displayType = displayType,
        cookTime = cookingTime.takeIf { it > 0 }
    )
}

private fun BukkitStonecuttingRecipe.toResolvedRecipe(air: ItemStack): ResolvedRecipe {
    val ingredient = inputChoice.toIngredient(air)
    return ResolvedRecipe(
        key = key,
        output = result.clone(),
        ingredients = listOf(ingredient) + List(8) { RecipeIngredient.empty(air) },
        source = RecipeSource.BUKKIT,
        displayType = RecipeDisplayType.STONECUTTER
    )
}

private fun SmithingTransformRecipe.toResolvedRecipe(air: ItemStack): ResolvedRecipe {
    val templateIng = template?.toIngredient(air) ?: RecipeIngredient.empty(air)
    val baseIng = base.toIngredient(air)
    val additionIng = addition.toIngredient(air)
    return ResolvedRecipe(
        key = key,
        output = result.clone(),
        ingredients = listOf(
            templateIng,
            baseIng,
            additionIng
        ) + List(6) { RecipeIngredient.empty(air) },
        source = RecipeSource.BUKKIT,
        displayType = RecipeDisplayType.SMITHING
    )
}
