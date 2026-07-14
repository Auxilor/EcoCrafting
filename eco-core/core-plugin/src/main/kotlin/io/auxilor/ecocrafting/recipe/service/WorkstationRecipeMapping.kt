package io.auxilor.ecocrafting.recipe.service

import com.willfp.eco.core.recipe.workstation.AnvilRecipe
import com.willfp.eco.core.recipe.workstation.BrewingRecipe
import com.willfp.eco.core.recipe.workstation.CrafterRecipe
import com.willfp.eco.core.recipe.workstation.GrindstoneRecipe
import com.willfp.eco.core.recipe.workstation.SmeltingRecipe
import com.willfp.eco.core.recipe.workstation.SmithingRecipe
import com.willfp.eco.core.recipe.workstation.StonecuttingRecipe
import com.willfp.eco.core.recipe.workstation.VillagerRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipe
import io.auxilor.ecocrafting.recipe.model.EcoCraftingMeta
import io.auxilor.ecocrafting.recipe.model.IngredientMatcher
import io.auxilor.ecocrafting.recipe.model.RecipeDisplayType
import io.auxilor.ecocrafting.recipe.model.RecipeIngredient
import io.auxilor.ecocrafting.recipe.model.RecipeSource
import io.auxilor.ecocrafting.recipe.model.ResolvedRecipe
import io.auxilor.ecocrafting.recipe.model.asDisplayAlternatives
import org.bukkit.inventory.ItemStack

// Maps every EcoCrafting-registered WorkstationRecipe subtype into the normalized
// 3x3-grid ResolvedRecipe shape the GUIs render.
internal fun WorkstationRecipe.toResolvedRecipe(air: ItemStack, meta: EcoCraftingMeta?): ResolvedRecipe {
    fun emptyIngredient() = RecipeIngredient.empty(air)
    fun displayOrAir(display: ItemStack?) = display?.clone() ?: air.clone()

    val ingredients: List<RecipeIngredient> = when (this) {
        is CrafterRecipe -> {
            val displays = partDisplays
            parts.mapIndexed { index, testable ->
                if (testable == null) emptyIngredient()
                else {
                    val alternatives = testable.allDisplayItems()
                    RecipeIngredient(
                        displayItem = displays.getOrNull(index)?.clone() ?: air.clone(),
                        matcher = IngredientMatcher.EcoPart(testable),
                        displayAlternatives = alternatives.asDisplayAlternatives()
                    )
                }
            }
        }
        is SmeltingRecipe -> {
            val alternatives = input.allDisplayItems()
            listOf(RecipeIngredient(
                displayItem = displayOrAir(inputDisplay),
                matcher = IngredientMatcher.EcoPart(input),
                displayAlternatives = alternatives.asDisplayAlternatives()
            )) + List(8) { emptyIngredient() }
        }
        is SmithingRecipe -> {
            val templateAlternatives = template.allDisplayItems()
            val baseAlternatives = base.allDisplayItems()
            val additionAlternatives = addition.allDisplayItems()
            listOf(
                RecipeIngredient(displayOrAir(templateDisplay), IngredientMatcher.EcoPart(template),
                    displayAlternatives = templateAlternatives.asDisplayAlternatives()),
                RecipeIngredient(displayOrAir(baseDisplay), IngredientMatcher.EcoPart(base),
                    displayAlternatives = baseAlternatives.asDisplayAlternatives()),
                RecipeIngredient(displayOrAir(additionDisplay), IngredientMatcher.EcoPart(addition),
                    displayAlternatives = additionAlternatives.asDisplayAlternatives())
            ) + List(6) { emptyIngredient() }
        }
        is StonecuttingRecipe -> {
            val alternatives = input.allDisplayItems()
            listOf(RecipeIngredient(
                displayItem = displayOrAir(inputDisplay),
                matcher = IngredientMatcher.EcoPart(input),
                displayAlternatives = alternatives.asDisplayAlternatives()
            )) + List(8) { emptyIngredient() }
        }
        is BrewingRecipe -> {
            val baseAlternatives = base.allDisplayItems()
            val ingredientAlternatives = ingredient.allDisplayItems()
            listOf(
                RecipeIngredient(base.item.clone(), IngredientMatcher.EcoPart(base),
                    displayAlternatives = baseAlternatives.asDisplayAlternatives()),
                RecipeIngredient(ingredient.item.clone(), IngredientMatcher.EcoPart(ingredient),
                    displayAlternatives = ingredientAlternatives.asDisplayAlternatives())
            ) + List(7) { emptyIngredient() }
        }
        is GrindstoneRecipe -> {
            val secondItem = item2
            val item1Alternatives = item1.allDisplayItems()
            listOfNotNull(
                RecipeIngredient(item1Display?.clone() ?: item1.item.clone(), IngredientMatcher.EcoPart(item1),
                    displayAlternatives = item1Alternatives.asDisplayAlternatives()),
                if (secondItem != null) {
                    val item2Alternatives = secondItem.allDisplayItems()
                    RecipeIngredient(item2Display?.clone() ?: secondItem.item.clone(), IngredientMatcher.EcoPart(secondItem),
                        displayAlternatives = item2Alternatives.asDisplayAlternatives())
                } else null
            ) + List(7) { emptyIngredient() }
        }
        is AnvilRecipe -> {
            val materialItem = material
            val baseAlternatives = base.allDisplayItems()
            listOfNotNull(
                RecipeIngredient(baseDisplay?.clone() ?: base.item.clone(), IngredientMatcher.EcoPart(base),
                    displayAlternatives = baseAlternatives.asDisplayAlternatives()),
                if (materialItem != null) {
                    val materialAlternatives = materialItem.allDisplayItems()
                    RecipeIngredient(materialDisplay?.clone() ?: materialItem.item.clone(), IngredientMatcher.EcoPart(materialItem),
                        displayAlternatives = materialAlternatives.asDisplayAlternatives())
                } else null
            ) + List(7) { emptyIngredient() }
        }
        is VillagerRecipe -> {
            val secondInput = input2
            val input1Alternatives = input1.allDisplayItems()
            listOfNotNull(
                RecipeIngredient(displayOrAir(input1Display), IngredientMatcher.EcoPart(input1),
                    displayAlternatives = input1Alternatives.asDisplayAlternatives()),
                if (secondInput != null) {
                    val secondInputAlternatives = secondInput.allDisplayItems()
                    RecipeIngredient(displayOrAir(input2Display), IngredientMatcher.EcoPart(secondInput),
                        displayAlternatives = secondInputAlternatives.asDisplayAlternatives())
                } else null
            ) + List(7) { emptyIngredient() }
        }
        else -> List(9) { emptyIngredient() }
    }

    return ResolvedRecipe(
        key = key,
        output = (output ?: air).clone(),
        ingredients = ingredients.normalizeToNine(air),
        permission = permission,
        source = RecipeSource.CUSTOM,
        displayType = meta?.displayType ?: RecipeDisplayType.CRAFTING,
        cookTime = (this as? SmeltingRecipe)?.cookTime?.takeIf { it > 0 },
        brewTime = (this as? BrewingRecipe)?.brewTime?.takeIf { it > 0 },
        villagerXp = (this as? VillagerRecipe)?.villagerXp?.takeIf { it > 0 }
    )
}
