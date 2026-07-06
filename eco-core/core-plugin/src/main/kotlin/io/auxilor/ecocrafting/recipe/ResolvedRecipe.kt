package io.auxilor.ecocrafting.recipe

import com.willfp.eco.core.recipe.parts.MaterialTestableItem
import com.willfp.eco.core.recipe.parts.EmptyTestableItem
import com.willfp.eco.core.items.TestableItem
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import io.auxilor.ecocrafting.custom.CustomRecipes
import io.auxilor.ecocrafting.custom.RecipeUnlockStore
import io.auxilor.ecocrafting.plugin

/**
 * A normalized crafting recipe displayed and optionally quick-crafted by EcoCrafting.
 * Ingredients are always normalized to a 3x3 grid for GUI display.
 */
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

fun ResolvedRecipe.withLockState(player: Player): ResolvedRecipe {
    if (source != RecipeSource.CUSTOM || key == null) return this
    val meta = CustomRecipes.getMeta(key) ?: return this
    return copy(locked = RecipeUnlockStore.isLocked(player, key, meta))
}

private val warnedInvalidRecipeIds = mutableSetOf<String>()

/**
 * Builds an "ecocrafting"-namespaced [NamespacedKey] from a config-supplied recipe id,
 * lowercasing it first since NamespacedKey rejects uppercase. Returns null (and logs a
 * one-time warning per offending value) instead of throwing on an invalid id.
 */
fun invalidRecipeIdKeyOrWarn(recipeId: String): NamespacedKey? {
    return try {
        NamespacedKey("ecocrafting", recipeId.lowercase())
    } catch (e: IllegalArgumentException) {
        if (warnedInvalidRecipeIds.add(recipeId)) {
            plugin.logger.warning("Invalid recipe id in config: '$recipeId'")
        }
        null
    }
}

enum class RecipeSource {
    ECO,
    BUKKIT,
    CUSTOM,
    UNKNOWN
}

enum class RecipeDisplayType {
    CRAFTING,
    SMELTING,
    BLAST_FURNACE,
    SMOKER,
    CAMPFIRE,
    SMITHING,
    STONECUTTER,
    CRAFTER,
    BREWING,
    GRINDSTONE,
    ANVIL,
    VILLAGER
}

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
            val probe = stack.clone()
            probe.amount = item.amount.coerceAtLeast(1)
            val expected = item.clone()
            expected.amount = item.amount.coerceAtLeast(1)
            return probe.isSimilar(expected)
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

fun IngredientMatcher.toTestableItem(): TestableItem = when (this) {
    is IngredientMatcher.Empty        -> EmptyTestableItem()
    is IngredientMatcher.EcoPart      -> part
    is IngredientMatcher.SimilarItem  -> MaterialTestableItem(item.type)
    is IngredientMatcher.MaterialOnly -> MaterialTestableItem(item.type)
    is IngredientMatcher.AnyItem      -> EmptyTestableItem()
}
