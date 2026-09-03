package com.exanthiax.ecocrafting.libreforge

import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import java.util.Collections
import java.util.WeakHashMap

// Registering under libreforge's own `craft` ID replaces its built-in trigger, which takes
// libreforge's CraftItemEvent listener with it - so this one carries that listener too, and
// vanilla crafts keep firing `craft` exactly as they did before EcoCrafting was installed.
// The vanilla-path logic below is libreforge's TriggerCraft verbatim, kept in sync with it.
//
// EcoCrafting's own recipes are dispatched by fireCraftEffects instead (that's the only way
// to cover the workstations vanilla has no CraftItemEvent for, and to carry the recipe key
// and workstation block), so CraftingTableListener marks the events it has already dispatched
// for and the handler here skips them rather than firing the trigger twice.
object TriggerCraft : Trigger("craft") {
    override val description = "Fires when the player crafts an item."

    override val categories = setOf("crafting")

    override val additionalInfo = listOf("Supports EcoCrafting custom recipes at every workstation.")

    override val parameterDescriptions = mapOf(
        TriggerParameter.LOCATION to "The player's location.",
        TriggerParameter.ITEM to "The crafted item.",
        TriggerParameter.VALUE to "The number of items crafted.",
        TriggerParameter.TEXT to "The recipe key, for EcoCrafting recipes.",
        TriggerParameter.BLOCK to "The workstation block, for EcoCrafting recipes."
    )

    private const val CRAFTING_FAILED = 0

    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.LOCATION,
        TriggerParameter.ITEM,
        TriggerParameter.VALUE,
        TriggerParameter.TEXT,
        TriggerParameter.BLOCK
    )

    // Weak so a craft that never reaches the handler below (EcoCrafting cancels the event when
    // it takes the craft over manually) drops out on its own instead of leaking the event.
    private val alreadyDispatched: MutableSet<CraftItemEvent> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    /**
     * Mark [event] as already dispatched through fireCraftEffects, so the vanilla handler
     * below leaves it alone.
     */
    internal fun markDispatched(event: CraftItemEvent) {
        alreadyDispatched += event
    }

    // MONITOR, not HIGH like libreforge's: CraftingTableListener runs at HIGHEST, so anything
    // earlier would fire before an EcoCrafting recipe has had the chance to mark the event.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun handle(event: CraftItemEvent) {
        if (alreadyDispatched.remove(event)) {
            return
        }

        if (event.result == Event.Result.DENY) {
            return
        }

        if (event.action == InventoryAction.NOTHING || event.inventory.result == null) {
            return
        }

        val player = event.whoClicked as? Player ?: return

        val item = event.recipe.result

        val cursor = event.cursor

        val recipeRepetitions = when (event.click) {
            ClickType.NUMBER_KEY -> handleNumberKeyCompletion(cursor, player, event)
            ClickType.DROP, ClickType.CONTROL_DROP -> handleDropCompletion(cursor)
            ClickType.SWAP_OFFHAND -> handleSwapOffhandCompletion(player)
            ClickType.SHIFT_RIGHT, ClickType.SHIFT_LEFT -> handleShiftClickCompletion(event, item)
            else -> 1
        }

        if (recipeRepetitions == CRAFTING_FAILED) {
            return
        }

        val totalItemsCrafted = item.amount * recipeRepetitions

        this.dispatch(
            player.toDispatcher(),
            TriggerData(
                player = player,
                item = item,
                value = totalItemsCrafted.toDouble(),
                text = (event.recipe as? org.bukkit.Keyed)?.key?.toString(),
                block = event.view.topInventory.location?.block
            )
        )
    }

    private fun handleShiftClickCompletion(event: CraftItemEvent, result: ItemStack): Int {
        val inventoryContent = event.view.topInventory.storageContents.toList().filterNot { item ->
            item == null || item.type.isAir
        }.drop(1)

        val playerInventory = event.view.bottomInventory as PlayerInventory

        val contents = playerInventory.storageContents.toList()

        val totalPossibleSlotsForItems = contents.sumOf { item ->
            val slotIsBlank = item == null || item.type.isAir

            if (slotIsBlank) {
                return@sumOf result.maxStackSize
            }

            val itemIsResult = item!!.isSimilar(result)

            if (itemIsResult) {
                return@sumOf result.maxStackSize - item.amount
            }

            0
        }

        if (totalPossibleSlotsForItems == 0) {
            return CRAFTING_FAILED
        }

        if (inventoryContent.isEmpty()) {
            return 0
        }

        val totalCraftableItems = inventoryContent.minOf { it!!.amount }

        return if (totalCraftableItems <= totalPossibleSlotsForItems) {
            totalCraftableItems
        } else {
            totalPossibleSlotsForItems / result.amount
        }
    }

    private fun handleSwapOffhandCompletion(player: Player): Int {
        val playerOffhandIsNotEmpty = player.inventory.itemInOffHand.type != Material.AIR
        // Can't craft into off-hand if off-hand is full.
        return if (playerOffhandIsNotEmpty) {
            CRAFTING_FAILED
        } else {
            1
        }
    }

    private fun handleDropCompletion(cursor: ItemStack?): Int {
        // Drop crafting with Q fails if cursor is full
        val curseIsNotEmpty = cursor != null && cursor.type != Material.AIR
        return if (curseIsNotEmpty) {
            CRAFTING_FAILED
        } else {
            1
        }
    }

    private fun handleNumberKeyCompletion(cursor: ItemStack?, player: Player, event: CraftItemEvent): Int {
        val cursorIsNotEmpty = cursor != null && cursor.type != Material.AIR
        val playerHasItemInSlot = player.inventory.getItem(event.hotbarButton) != null
        // Hotbar crafting fails if cursor contains item, or hotbar destination is not empty.
        return if (cursorIsNotEmpty || playerHasItemInSlot) {
            CRAFTING_FAILED
        } else {
            1
        }
    }
}
