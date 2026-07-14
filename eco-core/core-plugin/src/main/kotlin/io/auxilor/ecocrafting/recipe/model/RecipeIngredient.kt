package io.auxilor.ecocrafting.recipe.model

import com.willfp.eco.core.items.TestableItem
import com.willfp.eco.core.recipe.parts.EmptyTestableItem
import com.willfp.eco.core.recipe.parts.MaterialTestableItem
import com.willfp.eco.core.recipe.parts.TestableStack
import org.bukkit.inventory.ItemStack

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
