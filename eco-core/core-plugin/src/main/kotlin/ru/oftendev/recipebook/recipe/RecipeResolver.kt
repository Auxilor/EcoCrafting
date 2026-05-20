package ru.oftendev.recipebook.recipe

import com.google.common.collect.BiMap
import com.willfp.eco.core.items.CustomItem
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.recipe.Recipes
import com.willfp.eco.core.recipe.parts.EmptyTestableItem
import com.willfp.eco.core.recipe.recipes.CraftingRecipe
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe
import ru.oftendev.recipebook.custom.CustomRecipe
import ru.oftendev.recipebook.custom.CustomRecipes
import ru.oftendev.recipebook.custom.RecipeUnlockStore
import ru.oftendev.recipebook.integration.VaultPackIntegration
import ru.oftendev.recipebook.recipeBookPlugin
import java.lang.reflect.Field

object RecipeResolver {
    private val air = ItemStack(Material.AIR)

    fun canCraft(player: Player, itemStack: ItemStack): Boolean {
        val recipe = resolve(itemStack) ?: return true
        val permission = recipe.permission ?: return true
        return player.hasPermission(permission)
    }

    fun resolve(itemStack: ItemStack): ResolvedRecipe? {
        val clean = itemStack.clone().apply { amount = 1 }
        val customItem = Items.getCustomItem(clean)

        if (customItem != null) {
            VaultPackIntegration.resolveRecipe(customItem)?.let { return it }
            findEcoRecipe(customItem)?.let { return it }
        }

        CustomRecipes.getByOutput(clean)?.let { return it.toResolvedRecipe() }

        return findBukkitRecipe(clean)
    }

    fun resolveOutput(itemStack: ItemStack): ItemStack? {
        return resolve(itemStack)?.output?.clone()
    }

    fun resolveForPlayer(itemStack: ItemStack, player: Player): ResolvedRecipe? {
        val recipe = resolve(itemStack) ?: return null
        val locked = if (recipe.source == RecipeSource.CUSTOM) {
            val customRecipe = CustomRecipes.getByOutput(itemStack.clone().apply { amount = 1 })
            customRecipe?.let { RecipeUnlockStore.isLocked(player, it) } ?: false
        } else false
        return recipe.copy(locked = locked)
    }

    private fun CustomRecipe.toResolvedRecipe(): ResolvedRecipe {
        val airStack = ItemStack(Material.AIR)
        fun emptyIng() = RecipeIngredient.empty(airStack)

        val ingredients: List<RecipeIngredient> = when (this) {
            is CustomRecipe.CraftingTable -> parts
            is CustomRecipe.Smelting      -> listOf(input) + List(8) { emptyIng() }
            is CustomRecipe.Smithing      -> listOf(template, base, addition) + List(6) { emptyIng() }
            is CustomRecipe.Stonecutter   -> listOf(input) + List(8) { emptyIng() }
            is CustomRecipe.Crafter       -> parts
            is CustomRecipe.Brewing       -> listOf(base, ingredient) + List(7) { emptyIng() }
            is CustomRecipe.Grindstone    -> listOfNotNull(item1, item2) + List(7) { emptyIng() }
            is CustomRecipe.Anvil         -> listOfNotNull(base, material) + List(7) { emptyIng() }
            is CustomRecipe.Villager      -> listOfNotNull(input1, input2) + List(7) { emptyIng() }
        }

        return ResolvedRecipe(
            key = key,
            output = output.clone(),
            ingredients = ingredients,
            permission = permission,
            source = RecipeSource.CUSTOM,
            displayType = displayType
        )
    }

    fun findEcoRecipe(customItem: CustomItem): ResolvedRecipe? {
        val direct = listOf(
            customItem.key,
            NamespacedKey(customItem.key.namespace, customItem.key.key.removePrefix("set_"))
        ).firstNotNullOfOrNull { Recipes.getRecipe(it) }

        val recipe = direct ?: getEcoRecipes().firstOrNull { customItem.matches(it.output) }
        return recipe?.toResolvedRecipe()
    }

    fun getEcoRecipes(): Collection<CraftingRecipe> {
        return runCatching { getRecipesBiMap().values.toList() }
            .onFailure { recipeBookPlugin.logger.warning("[RecipeBook] Could not read eco recipe registry: ${it.message}") }
            .getOrDefault(emptyList())
    }

    @Suppress("UNCHECKED_CAST")
    private fun getRecipesBiMap(): BiMap<NamespacedKey, CraftingRecipe> {
        val field: Field = Recipes::class.java.getDeclaredField("RECIPES")
        field.isAccessible = true
        return field.get(null) as BiMap<NamespacedKey, CraftingRecipe>
    }

    private fun CraftingRecipe.toResolvedRecipe(): ResolvedRecipe {
        val ingredients = parts.map { part ->
            if (part is EmptyTestableItem) {
                RecipeIngredient.empty(air)
            } else {
                RecipeIngredient(
                    part.item.clone(),
                    IngredientMatcher.EcoPart(part)
                )
            }
        }.normalizeToNine()

        return ResolvedRecipe(
            key = key,
            output = output.clone(),
            ingredients = ingredients,
            permission = permission,
            source = RecipeSource.ECO,
            shapeless = parts.size != 9
        )
    }

    private fun findBukkitRecipe(stack: ItemStack): ResolvedRecipe? {
        return Bukkit.getRecipesFor(stack).firstNotNullOfOrNull { recipe ->
            when (recipe) {
                is ShapedRecipe -> recipe.toResolvedRecipe()
                is ShapelessRecipe -> recipe.toResolvedRecipe()
                else -> null
            }
        }
    }

    private fun ShapedRecipe.toResolvedRecipe(): ResolvedRecipe {
        val ingredientGrid = MutableList(9) { RecipeIngredient.empty(air) }
        val shape = this.shape
        for (row in shape.indices) {
            if (row >= 3) break
            val line = shape[row]
            for (col in 0 until line.length.coerceAtMost(3)) {
                val choice = choiceMap[line[col]] ?: continue
                ingredientGrid[row * 3 + col] = choice.toIngredient()
            }
        }

        return ResolvedRecipe(
            key = key,
            output = result.clone(),
            ingredients = ingredientGrid,
            source = RecipeSource.BUKKIT
        )
    }

    private fun ShapelessRecipe.toResolvedRecipe(): ResolvedRecipe {
        val ingredients = choiceList.map { it.toIngredient() }.normalizeToNine()
        return ResolvedRecipe(
            key = key,
            output = result.clone(),
            ingredients = ingredients,
            source = RecipeSource.BUKKIT,
            shapeless = true
        )
    }

    private fun RecipeChoice.toIngredient(): RecipeIngredient {
        val display = displayIcon.clone()
        val matcher = when (this) {
            is RecipeChoice.ExactChoice -> IngredientMatcher.SimilarItem(display)
            is RecipeChoice.MaterialChoice -> IngredientMatcher.MaterialOnly(display)
            else -> IngredientMatcher.SimilarItem(display)
        }
        return RecipeIngredient(display, matcher)
    }

    private val RecipeChoice.displayIcon: ItemStack
        get() = when (this) {
            is RecipeChoice.ExactChoice -> this.choices.firstOrNull()?.clone() ?: air.clone()
            is RecipeChoice.MaterialChoice -> this.choices.firstOrNull()?.let { ItemStack(it) } ?: air.clone()
            else -> this.itemStack?.clone() ?: air.clone()
        }

    private fun List<RecipeIngredient>.normalizeToNine(): List<RecipeIngredient> {
        if (size == 9) return this
        return take(9) + List((9 - size).coerceAtLeast(0)) { RecipeIngredient.empty(air) }
    }

}
