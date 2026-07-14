package io.auxilor.ecocrafting.crafting.integration

import io.auxilor.ecocrafting.EcoCraftingPlugin
import org.bukkit.entity.Player
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

internal fun maxCraftsFromGrid(matrix: Array<out ItemStack?>): Int {
    val present = matrix.filter { it != null && !it.type.isAir }
    if (present.isEmpty()) return Int.MAX_VALUE
    return present.minOf { it!!.amount }
}

internal fun maxCraftsFromInput(inputStack: ItemStack?): Int =
    inputStack?.amount ?: 0

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
