package ru.oftendev.recipebook.custom

import com.willfp.libreforge.conditions.ConditionList
import com.willfp.libreforge.effects.Chain
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.recipe.RecipeDisplayType
import ru.oftendev.recipebook.recipe.RecipeIngredient

enum class SmeltingType {
    FURNACE, BLAST_FURNACE, SMOKER, CAMPFIRE
}

data class StonecutterOutput(
    val item: ItemStack,
    val ghost: Boolean,
    val ghostChain: Chain?
)

sealed class CustomRecipe {
    abstract val key: NamespacedKey
    abstract val output: ItemStack
    abstract val permission: String?
    abstract val ghost: Boolean
    abstract val ghostChain: Chain?
    abstract val visibilityConditions: ConditionList
    abstract val craftingConditions: ConditionList
    abstract val lockedByDefault: Boolean
    abstract val showWhenLocked: Boolean
    abstract val lockedLore: List<String>
    abstract val unlockConditions: ConditionList
    abstract val displayType: RecipeDisplayType

    data class CraftingTable(
        override val key: NamespacedKey,
        override val output: ItemStack,
        val parts: List<RecipeIngredient>,
        val shapeless: Boolean,
        val symmetry: Boolean,
        override val permission: String?,
        override val ghost: Boolean,
        override val ghostChain: Chain?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList,
        override val lockedByDefault: Boolean,
        override val showWhenLocked: Boolean,
        override val lockedLore: List<String>,
        override val unlockConditions: ConditionList
    ) : CustomRecipe() {
        override val displayType = RecipeDisplayType.CRAFTING
    }

    data class Smelting(
        override val key: NamespacedKey,
        override val output: ItemStack,
        val input: RecipeIngredient,
        val stationType: SmeltingType,
        val cookTime: Int,
        val experience: Float,
        override val permission: String?,
        override val ghost: Boolean,
        override val ghostChain: Chain?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList,
        override val lockedByDefault: Boolean,
        override val showWhenLocked: Boolean,
        override val lockedLore: List<String>,
        override val unlockConditions: ConditionList
    ) : CustomRecipe() {
        override val displayType = RecipeDisplayType.SMELTING
    }

    data class Smithing(
        override val key: NamespacedKey,
        override val output: ItemStack,
        val template: RecipeIngredient,
        val base: RecipeIngredient,
        val addition: RecipeIngredient,
        override val permission: String?,
        override val ghost: Boolean,
        override val ghostChain: Chain?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList,
        override val lockedByDefault: Boolean,
        override val showWhenLocked: Boolean,
        override val lockedLore: List<String>,
        override val unlockConditions: ConditionList
    ) : CustomRecipe() {
        override val displayType = RecipeDisplayType.SMITHING
    }

    data class Stonecutter(
        override val key: NamespacedKey,
        val input: RecipeIngredient,
        val outputs: List<StonecutterOutput>,
        override val permission: String?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList,
        override val lockedByDefault: Boolean,
        override val showWhenLocked: Boolean,
        override val lockedLore: List<String>,
        override val unlockConditions: ConditionList
    ) : CustomRecipe() {
        override val output: ItemStack get() = outputs.first().item
        override val ghost: Boolean get() = outputs.any { it.ghost }
        override val ghostChain: Chain? get() = null
        override val displayType = RecipeDisplayType.STONECUTTER
    }

    data class Crafter(
        override val key: NamespacedKey,
        override val output: ItemStack,
        val parts: List<RecipeIngredient>,
        val shapeless: Boolean,
        override val permission: String?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList
    ) : CustomRecipe() {
        override val ghost = false
        override val ghostChain: Chain? = null
        override val lockedByDefault = false
        override val showWhenLocked = false
        override val lockedLore: List<String> = emptyList()
        override val unlockConditions = ConditionList(emptyList())
        override val displayType = RecipeDisplayType.CRAFTER
    }

    data class Brewing(
        override val key: NamespacedKey,
        override val output: ItemStack,
        val base: RecipeIngredient,
        val ingredient: RecipeIngredient,
        override val permission: String?,
        override val ghost: Boolean,
        override val ghostChain: Chain?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList,
        override val lockedByDefault: Boolean,
        override val showWhenLocked: Boolean,
        override val lockedLore: List<String>,
        override val unlockConditions: ConditionList
    ) : CustomRecipe() {
        override val displayType = RecipeDisplayType.BREWING
    }

    data class Cartography(
        override val key: NamespacedKey,
        override val output: ItemStack,
        val map: RecipeIngredient,
        val addition: RecipeIngredient,
        override val permission: String?,
        override val ghost: Boolean,
        override val ghostChain: Chain?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList,
        override val lockedByDefault: Boolean,
        override val showWhenLocked: Boolean,
        override val lockedLore: List<String>,
        override val unlockConditions: ConditionList
    ) : CustomRecipe() {
        override val displayType = RecipeDisplayType.CARTOGRAPHY
    }

    data class Grindstone(
        override val key: NamespacedKey,
        override val output: ItemStack,
        val item1: RecipeIngredient,
        val item2: RecipeIngredient?,
        override val permission: String?,
        override val ghost: Boolean,
        override val ghostChain: Chain?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList,
        override val lockedByDefault: Boolean,
        override val showWhenLocked: Boolean,
        override val lockedLore: List<String>,
        override val unlockConditions: ConditionList
    ) : CustomRecipe() {
        override val displayType = RecipeDisplayType.GRINDSTONE
    }

    data class Anvil(
        override val key: NamespacedKey,
        override val output: ItemStack,
        val base: RecipeIngredient,
        val material: RecipeIngredient?,
        val resultName: String?,
        val repairCost: Int,
        override val permission: String?,
        override val ghost: Boolean,
        override val ghostChain: Chain?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList,
        override val lockedByDefault: Boolean,
        override val showWhenLocked: Boolean,
        override val lockedLore: List<String>,
        override val unlockConditions: ConditionList
    ) : CustomRecipe() {
        override val displayType = RecipeDisplayType.ANVIL
    }

    data class Villager(
        override val key: NamespacedKey,
        override val output: ItemStack,
        val input1: RecipeIngredient,
        val input2: RecipeIngredient?,
        override val permission: String?,
        override val ghost: Boolean,
        override val ghostChain: Chain?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList,
        override val lockedByDefault: Boolean,
        override val showWhenLocked: Boolean,
        override val lockedLore: List<String>,
        override val unlockConditions: ConditionList
    ) : CustomRecipe() {
        override val displayType = RecipeDisplayType.VILLAGER
    }
}
