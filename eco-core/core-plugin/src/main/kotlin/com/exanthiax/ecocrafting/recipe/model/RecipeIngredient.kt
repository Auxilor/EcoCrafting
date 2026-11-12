package com.exanthiax.ecocrafting.recipe.model

import org.bukkit.inventory.ItemStack
import wiki.ecohub.game.item.lookup.EmptyTestableItem
import wiki.ecohub.game.item.lookup.MaterialTestableItem
import wiki.ecohub.game.item.lookup.TestableItem
import wiki.ecohub.game.item.lookup.TestableStack

data class RecipeIngredient(
    val displayItem: ItemStack,
    val matcher: IngredientMatcher,
    val empty: Boolean = false,
    val displayAlternatives: List<ItemStack> = emptyList()
) {
    val allDisplayItems: List<ItemStack>
        get() = displayAlternatives.ifEmpty { listOf(displayItem) }

    fun matches(stack: ItemStack?): Boolean = !empty && matcher.matches(stack)

    companion object {
        fun empty(air: ItemStack): RecipeIngredient {
            return RecipeIngredient(air, IngredientMatcher.Empty, true)
        }
    }
}

sealed interface IngredientMatcher {
    fun matches(stack: ItemStack?): Boolean

    data object Empty : IngredientMatcher {
        override fun matches(stack: ItemStack?): Boolean = stack == null || stack.type.isAir
    }

    data class EcoPart(val part: TestableItem) : IngredientMatcher {
        override fun matches(stack: ItemStack?): Boolean = stack != null && part.matches(stack)
    }

    data class SimilarItem(val item: ItemStack) : IngredientMatcher {
        override fun matches(stack: ItemStack?): Boolean {
            if (stack == null || stack.type.isAir) return false
            if (stack.amount < item.amount.coerceAtLeast(1)) return false
            return stack.isSimilar(item)
        }
    }

    data class MaterialOnly(val item: ItemStack) : IngredientMatcher {
        override fun matches(stack: ItemStack?): Boolean {
            if (stack == null || stack.type.isAir) return false
            // Vanilla material requirements should not consume custom/PDC-tagged items.
            val meta = stack.itemMeta
            if (meta != null && !meta.persistentDataContainer.isEmpty) return false
            return stack.type == item.type
        }
    }

    data object AnyItem : IngredientMatcher {
        override fun matches(stack: ItemStack?): Boolean = stack != null && !stack.type.isAir
    }
}

// `displayAlternatives` is only meaningful when there's more than one item to show;
// a single-item list is represented as empty so `allDisplayItems` falls back to `displayItem`.
fun List<ItemStack>.asDisplayAlternatives(): List<ItemStack> = if (size > 1) this else emptyList()

fun IngredientMatcher.toTestableItem(): TestableItem = when (this) {
    is IngredientMatcher.Empty        -> EmptyTestableItem()
    is IngredientMatcher.EcoPart      -> part
    is IngredientMatcher.SimilarItem  ->
        if (item.amount > 1) TestableStack(MaterialTestableItem(item.type), item.amount)
        else MaterialTestableItem(item.type)
    is IngredientMatcher.MaterialOnly -> MaterialTestableItem(item.type)
    is IngredientMatcher.AnyItem      -> EmptyTestableItem()
}

fun TestableItem.requiredAmount(): Int = (this as? TestableStack)?.amount ?: 1

// A TestableStack folds its size requirement into matches(), which is no use to a
// caller that has to tell "wrong item" apart from "right item, not enough of it".
fun TestableItem.matchesIgnoringAmount(stack: ItemStack?): Boolean =
    (this as? TestableStack)?.handle?.matches(stack) ?: matches(stack)
