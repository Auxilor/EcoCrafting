package ru.oftendev.recipebook.recipe

import com.google.common.collect.BiMap
import com.willfp.eco.core.items.CustomItem
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.recipe.Recipes
import com.willfp.eco.core.recipe.parts.EmptyTestableItem
import com.willfp.eco.core.recipe.recipes.CraftingRecipe
import com.willfp.eco.core.recipe.workstation.AnvilRecipe
import com.willfp.eco.core.recipe.workstation.BrewingRecipe
import com.willfp.eco.core.recipe.workstation.CrafterRecipe
import com.willfp.eco.core.recipe.workstation.GrindstoneRecipe
import com.willfp.eco.core.recipe.workstation.SmeltingRecipe
import com.willfp.eco.core.recipe.workstation.SmithingRecipe
import com.willfp.eco.core.recipe.workstation.StonecuttingRecipe
import com.willfp.eco.core.recipe.workstation.VillagerRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe
import ru.oftendev.recipebook.custom.CustomRecipes
import ru.oftendev.recipebook.custom.RecipeSymmetry
import ru.oftendev.recipebook.custom.RecipeUnlockStore
import ru.oftendev.recipebook.integration.VaultPackIntegration
import ru.oftendev.recipebook.recipeBookPlugin
import java.lang.reflect.Field

object RecipeResolver {
    private val air = ItemStack(Material.AIR)

    private val SYMMETRY_TRANSFORMS = listOf(
        intArrayOf(0,1,2,3,4,5,6,7,8),
        RecipeSymmetry.ROT_90_CW,
        RecipeSymmetry.ROT_180,
        RecipeSymmetry.ROT_270_CW,
        RecipeSymmetry.MIRROR_H,
        intArrayOf(0,3,6,1,4,7,2,5,8),  // transpose (MIRROR_H · ROT_90_CW)
        intArrayOf(6,7,8,3,4,5,0,1,2),  // vertical flip (MIRROR_H · ROT_180)
        intArrayOf(8,5,2,7,4,1,6,3,0),  // anti-diagonal (MIRROR_H · ROT_270_CW)
    )

    private fun List<RecipeIngredient>.symmetryKey(): String {
        val types = map { it.displayItem.type.name }
        return SYMMETRY_TRANSFORMS
            .map { t -> t.map { types[it] }.joinToString(",") }
            .min()
    }

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

        findWorkstationRecipeByOutput(clean)?.let { return it }

        return findBukkitRecipe(clean)
    }

    fun resolveOutput(itemStack: ItemStack): ItemStack? {
        return resolve(itemStack)?.output?.clone()
    }

    fun resolveAll(itemStack: ItemStack): List<ResolvedRecipe> {
        val clean = itemStack.clone().apply { amount = 1 }
        val customItem = Items.getCustomItem(clean)
        val results = mutableListOf<ResolvedRecipe>()

        if (customItem != null) {
            VaultPackIntegration.resolveRecipe(customItem)?.let { results += it }
            findEcoRecipe(customItem)?.let { results += it }
        }

        results += findAllWorkstationRecipesByOutput(clean)

        findAllBukkitRecipes(clean).forEach { bukkit ->
            if (results.none { it.key == bukkit.key }) results += bukkit
        }

        val sorted = results.sortedBy { it.displayType.name }

        if (!recipeBookPlugin.configYml.getBool("deduplicate-symmetrical")) return sorted

        val seen = mutableSetOf<String>()
        return sorted.filter { recipe ->
            val isShaped = recipe.displayType == RecipeDisplayType.CRAFTING ||
                recipe.displayType == RecipeDisplayType.CRAFTER
            if (!isShaped || recipe.shapeless) {
                true
            } else {
                val key = "${recipe.output.type.name}:${recipe.ingredients.symmetryKey()}"
                seen.add(key)
            }
        }
    }

    fun resolveForPlayer(itemStack: ItemStack, player: Player): ResolvedRecipe? {
        val recipe = resolve(itemStack) ?: return null
        val locked = if (recipe.source == RecipeSource.CUSTOM && recipe.key != null) {
            val meta = CustomRecipes.getMeta(recipe.key)
            if (meta != null) RecipeUnlockStore.isLocked(player, recipe.key, meta) else false
        } else false
        return recipe.copy(locked = locked)
    }

    private fun findWorkstationRecipeByOutput(clean: ItemStack): ResolvedRecipe? {
        return WorkstationRecipes.getAll()
            .firstOrNull { recipe -> recipe.output?.let { it.isSimilar(clean) } == true && CustomRecipes.getMeta(recipe.key) != null }
            ?.workstationToResolvedRecipe()
    }

    private fun findAllWorkstationRecipesByOutput(clean: ItemStack): List<ResolvedRecipe> {
        return WorkstationRecipes.getAll()
            .filter { recipe -> recipe.output?.let { it.isSimilar(clean) } == true && CustomRecipes.getMeta(recipe.key) != null }
            .map { it.workstationToResolvedRecipe() }
    }

    private fun WorkstationRecipe.workstationToResolvedRecipe(): ResolvedRecipe {
        fun emptyIng() = RecipeIngredient.empty(air)
        fun displayOrAir(display: ItemStack?) = display?.clone() ?: air.clone()

        val ingredients: List<RecipeIngredient> = when (this) {
            is CrafterRecipe -> {
                val displays = partDisplays
                parts.mapIndexed { idx, testable ->
                    if (testable == null) emptyIng()
                    else RecipeIngredient(
                        displays.getOrNull(idx)?.clone() ?: air.clone(),
                        IngredientMatcher.EcoPart(testable)
                    )
                }
            }
            is SmeltingRecipe -> {
                val display = displayOrAir(inputDisplay)
                listOf(RecipeIngredient(display, IngredientMatcher.EcoPart(input))) +
                    List(8) { emptyIng() }
            }
            is SmithingRecipe -> {
                listOf(
                    RecipeIngredient(displayOrAir(templateDisplay), IngredientMatcher.EcoPart(template)),
                    RecipeIngredient(displayOrAir(baseDisplay), IngredientMatcher.EcoPart(base)),
                    RecipeIngredient(displayOrAir(additionDisplay), IngredientMatcher.EcoPart(addition))
                ) + List(6) { emptyIng() }
            }
            is StonecuttingRecipe -> {
                listOf(RecipeIngredient(displayOrAir(inputDisplay), IngredientMatcher.EcoPart(input))) +
                    List(8) { emptyIng() }
            }
            is BrewingRecipe -> {
                listOf(
                    RecipeIngredient(base.item.clone(), IngredientMatcher.EcoPart(base)),
                    RecipeIngredient(ingredient.item.clone(), IngredientMatcher.EcoPart(ingredient))
                ) + List(7) { emptyIng() }
            }
            is GrindstoneRecipe -> {
                val secondItem = item2
                listOfNotNull(
                    RecipeIngredient(item1.item.clone(), IngredientMatcher.EcoPart(item1)),
                    if (secondItem != null) RecipeIngredient(secondItem.item.clone(), IngredientMatcher.EcoPart(secondItem)) else null
                ) + List(7) { emptyIng() }
            }
            is AnvilRecipe -> {
                val materialItem = material
                listOfNotNull(
                    RecipeIngredient(base.item.clone(), IngredientMatcher.EcoPart(base)),
                    if (materialItem != null) RecipeIngredient(materialItem.item.clone(), IngredientMatcher.EcoPart(materialItem)) else null
                ) + List(7) { emptyIng() }
            }
            is VillagerRecipe -> {
                val i2 = input2
                listOfNotNull(
                    RecipeIngredient(displayOrAir(input1Display), IngredientMatcher.EcoPart(input1)),
                    if (i2 != null) RecipeIngredient(displayOrAir(input2Display), IngredientMatcher.EcoPart(i2)) else null
                ) + List(7) { emptyIng() }
            }
            else -> List(9) { emptyIng() }
        }

        val meta = CustomRecipes.getMeta(key)
        return ResolvedRecipe(
            key = key,
            output = (output ?: air).clone(),
            ingredients = ingredients.normalizeToNine(),
            permission = permission,
            source = RecipeSource.CUSTOM,
            displayType = meta?.displayType ?: RecipeDisplayType.CRAFTING,
            cookTime = (this as? SmeltingRecipe)?.cookTime?.takeIf { it > 0 }
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
        val strict = recipeBookPlugin.configYml.getBool("strict-item-matching")
        return Bukkit.getRecipesFor(stack).firstNotNullOfOrNull { recipe ->
            val resolved = when (recipe) {
                is ShapedRecipe -> recipe.toResolvedRecipe()
                is ShapelessRecipe -> recipe.toResolvedRecipe()
                else -> null
            } ?: return@firstNotNullOfOrNull null
            if (strict && !resolved.output.isSimilar(stack)) null else resolved
        }
    }

    private fun findAllBukkitRecipes(stack: ItemStack): List<ResolvedRecipe> {
        val strict = recipeBookPlugin.configYml.getBool("strict-item-matching")
        return Bukkit.getRecipesFor(stack).mapNotNull { recipe ->
            val resolved = when (recipe) {
                is ShapedRecipe -> recipe.toResolvedRecipe()
                is ShapelessRecipe -> recipe.toResolvedRecipe()
                else -> null
            } ?: return@mapNotNull null
            if (strict && !resolved.output.isSimilar(stack)) null else resolved
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
