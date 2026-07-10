package io.auxilor.ecocrafting.recipe.service

import com.google.common.collect.BiMap
import com.willfp.eco.core.items.CustomItem
import com.willfp.eco.core.recipe.Recipes
import com.willfp.eco.core.recipe.parts.EmptyTestableItem
import com.willfp.eco.core.recipe.recipes.CraftingRecipe
import io.auxilor.ecocrafting.EcoCraftingPlugin
import io.auxilor.ecocrafting.recipe.model.IngredientMatcher
import io.auxilor.ecocrafting.recipe.model.RecipeIngredient
import io.auxilor.ecocrafting.recipe.model.RecipeSource
import io.auxilor.ecocrafting.recipe.model.ResolvedRecipe
import java.lang.reflect.Field
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack

// Maps eco's own CraftingRecipe registry (Recipes.RECIPES) into ResolvedRecipe.

internal fun findEcoRecipe(customItem: CustomItem, air: ItemStack, plugin: EcoCraftingPlugin): ResolvedRecipe? {
    val direct = listOf(
        customItem.key,
        NamespacedKey(customItem.key.namespace, customItem.key.key.removePrefix("set_"))
    ).firstNotNullOfOrNull { Recipes.getRecipe(it) }

    val recipe = direct ?: getEcoRecipes(plugin).firstOrNull { customItem.matches(it.output) }
    return recipe?.toResolvedRecipe(air)
}

internal fun getEcoRecipes(plugin: EcoCraftingPlugin): Collection<CraftingRecipe> {
    return try {
        getRecipesBiMap().values.toList()
    } catch (e: Exception) {
        plugin.logger.warning("Could not read eco recipe registry: ${e.message}")
        emptyList()
    }
}

// Reflects into eco's private Recipes.RECIPES field (verified against eco
// 2026.27.1). If eco renames it, getEcoRecipes logs a warning and returns
// empty. Remove once eco exposes a public recipe-registry accessor.
@Suppress("UNCHECKED_CAST")
private fun getRecipesBiMap(): BiMap<NamespacedKey, CraftingRecipe> {
    val field: Field = Recipes::class.java.getDeclaredField("RECIPES")
    field.isAccessible = true
    return field.get(null) as BiMap<NamespacedKey, CraftingRecipe>
}

internal fun CraftingRecipe.toResolvedRecipe(air: ItemStack): ResolvedRecipe {
    val ingredients = parts.map { part ->
        if (part is EmptyTestableItem) {
            RecipeIngredient.empty(air)
        } else {
            val displayItems = part.allDisplayItems()
            RecipeIngredient(
                displayItem = displayItems.first(),
                matcher = IngredientMatcher.EcoPart(part),
                displayAlternatives = if (displayItems.size > 1) displayItems else emptyList()
            )
        }
    }.normalizeToNine(air)

    return ResolvedRecipe(
        key = key,
        output = output.clone(),
        ingredients = ingredients,
        permission = permission,
        source = RecipeSource.ECO,
        shapeless = parts.size != 9
    )
}
