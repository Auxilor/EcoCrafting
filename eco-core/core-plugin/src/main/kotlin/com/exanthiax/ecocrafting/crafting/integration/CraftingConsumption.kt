package com.exanthiax.ecocrafting.crafting.integration

import com.willfp.eco.core.items.TestableItem
import com.willfp.eco.core.recipe.workstation.CrafterRecipe
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.recipe.model.RecipeSymmetry
import com.exanthiax.ecocrafting.recipe.model.matchesIgnoringAmount
import com.exanthiax.ecocrafting.recipe.model.requiredAmount
import org.bukkit.entity.Player
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

// Base-orientation first, then the same rotations/mirrors RecipeLoader generates symmetry
// variants from - only the base recipe is kept as a CrafterRecipe, so a variant's craft
// arrives with the grid rotated out from under its parts.
private val gridOrientations: List<IntArray> = listOf(
    IntArray(9) { it },
    RecipeSymmetry.ROT_90_CW,
    RecipeSymmetry.ROT_180,
    RecipeSymmetry.ROT_270_CW,
    RecipeSymmetry.MIRROR_H,
    IntArray(9) { RecipeSymmetry.ROT_90_CW[RecipeSymmetry.MIRROR_H[it]] },
    IntArray(9) { RecipeSymmetry.ROT_180[RecipeSymmetry.MIRROR_H[it]] },
    IntArray(9) { RecipeSymmetry.ROT_270_CW[RecipeSymmetry.MIRROR_H[it]] }
)

// How much of each filled grid slot one craft consumes, or null when the grid doesn't lay
// the recipe out at all. Deliberately blind to stack size: a slot holding too little still
// reports its requirement, because Bukkit's ExactChoice ignores stack size and would let
// vanilla craft a `<item> 4` part from a single item unless the caller can see the shortfall
// and refuse. Slots left out of the map need one item, the vanilla assumption.
internal fun gridRequirements(recipe: CrafterRecipe, matrix: Array<out ItemStack?>): Map<Int, Int>? {
    if (matrix.size != 9) return null
    val parts: List<TestableItem?> = recipe.parts
    if (recipe.isShapeless) return shapelessRequirements(parts, matrix)
    for (orientation in gridOrientations) {
        shapedRequirements(parts, matrix, orientation)?.let { return it }
    }
    return null
}

private fun shapedRequirements(
    parts: List<TestableItem?>,
    matrix: Array<out ItemStack?>,
    orientation: IntArray
): Map<Int, Int>? {
    if (parts.size != 9) return null
    val requirements = mutableMapOf<Int, Int>()
    for (slot in 0 until 9) {
        val part = parts[orientation[slot]]
        val stack = matrix[slot]
        if (part == null) {
            if (stack != null && !stack.isEmpty) return null
            continue
        }
        if (!part.matchesIgnoringAmount(stack)) return null
        requirements[slot] = part.requiredAmount()
    }
    return requirements
}

private fun shapelessRequirements(parts: List<TestableItem?>, matrix: Array<out ItemStack?>): Map<Int, Int>? {
    val unassigned = parts.filterNotNull().toMutableList()
    val filled = matrix.indices.filter { matrix[it]?.isEmpty == false }
    if (unassigned.size != filled.size) return null
    val requirements = mutableMapOf<Int, Int>()
    for (slot in filled) {
        val index = unassigned.indexOfFirst { it.matchesIgnoringAmount(matrix[slot]) }
        if (index < 0) return null
        requirements[slot] = unassigned.removeAt(index).requiredAmount()
    }
    return requirements
}

internal fun gridSatisfies(matrix: Array<out ItemStack?>, requirements: Map<Int, Int>): Boolean =
    requirements.all { (slot, required) -> (matrix.getOrNull(slot)?.amount ?: 0) >= required }

internal fun maxCraftsFromGrid(matrix: Array<out ItemStack?>, requirements: Map<Int, Int>): Int {
    val filled = matrix.indices.filter { matrix[it]?.isEmpty == false }
    if (filled.isEmpty()) return Int.MAX_VALUE
    return filled.minOf { matrix[it]!!.amount / (requirements[it] ?: 1).coerceAtLeast(1) }
}

internal fun maxCraftsFromInput(inputStack: ItemStack?, requiredAmount: Int): Int =
    (inputStack?.amount ?: 0) / requiredAmount.coerceAtLeast(1)

internal fun consume(inventory: Inventory, slot: Int, amount: Int = 1) {
    val stack = inventory.getItem(slot) ?: return
    val remaining = stack.amount - amount
    if (remaining <= 0) inventory.setItem(slot, null)
    else { stack.amount = remaining; inventory.setItem(slot, stack) }
}

internal fun calculateCraftAmount(plugin: EcoCraftingPlugin, event: CraftItemEvent, ingredientBasedAmount: Int): Int {
    return if (event.isShiftClick) {
        val result = event.recipe.result
        val player = event.whoClicked as Player
        val spaceBased = spaceBasedAmount(player, result)
        val amount = minOf(spaceBased, ingredientBasedAmount).coerceAtLeast(1)
        if (ingredientBasedAmount < spaceBased) {
            plugin.debug("[CraftAmount] capped by ingredients: space=$spaceBased ingredients=$ingredientBasedAmount -> $amount")
        }
        amount
    } else 1
}

internal fun spaceBasedAmount(player: Player, result: ItemStack): Int {
    val freeSpace = player.inventory.storageContents.sumOf { slot ->
        when {
            slot == null || slot.type.isAir -> result.maxStackSize
            slot.isSimilar(result) -> result.maxStackSize - slot.amount
            else -> 0
        }
    }
    return (freeSpace / result.amount.coerceAtLeast(1)).coerceAtLeast(1)
}

// UX difference from vanilla: grindstone/no-item results go straight to the inventory
// (dropped at the player's feet if full) instead of onto the cursor, since GrindstoneInventory
// has no consumption-amount API to fix shift-click over-consumption otherwise. Anvil prefers
// the cursor (matching vanilla) when it's free and the click wasn't a shift-click.
internal fun giveOrDropItem(player: Player, item: ItemStack, preferCursor: Boolean = false) {
    if (preferCursor) {
        val cursor = player.itemOnCursor
        if (cursor.type.isAir) {
            player.setItemOnCursor(item)
            return
        }
        if (cursor.isSimilar(item) && cursor.amount + item.amount <= cursor.maxStackSize) {
            cursor.amount += item.amount
            player.setItemOnCursor(cursor)
            return
        }
    }
    player.inventory.addItem(item).values.forEach { player.world.dropItem(player.location, it) }
}
