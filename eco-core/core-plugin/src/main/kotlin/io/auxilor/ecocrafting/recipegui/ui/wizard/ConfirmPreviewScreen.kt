package io.auxilor.ecocrafting.recipegui.ui.wizard

import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.util.formatEco
import io.auxilor.ecocrafting.recipegui.service.PendingRecipe
import io.auxilor.ecocrafting.recipegui.ui.RecipeCreatorGUI
import org.bukkit.Material
import org.bukkit.entity.Player

internal fun RecipeCreatorGUI.openConfirmPreview(player: Player, pending: PendingRecipe) {
    val slotLayout = ingredientSlotLayout(pending.typeKey)
    val outputSlot = 3 to 4
    val cancelSlot = 6 to 3
    val confirmSlot = 6 to 5

    val builtMenu = menu(6) {
        title = "&8New Recipe - Confirm"

        slotLayout.forEachIndexed { index, (row, col) ->
            pending.parts[index]?.let { setSlot(row, col, Slot.builder(it.clone()).build()) }
        }
        setSlot(outputSlot.first, outputSlot.second, Slot.builder(pending.output.clone()).build())

        setSlot(cancelSlot.first, cancelSlot.second, Slot.builder(
            ItemStackBuilder(Material.RED_WOOL).setDisplayName("&c✗ Cancel".formatEco()).build()
        ).onLeftClick { _, _ ->
            player.closeInventory()
            cancelSave(player)
        }.build())

        setSlot(confirmSlot.first, confirmSlot.second, Slot.builder(
            ItemStackBuilder(Material.GREEN_WOOL).setDisplayName("&a✓ Confirm".formatEco()).build()
        ).onLeftClick { _, _ ->
            player.closeInventory()
            confirmSave(player)
        }.build())

        addWorkstationIcons(pending.typeKey)
        val used = slotLayout.toSet() + setOf(outputSlot, cancelSlot, confirmSlot) +
            workstationIconPositions.map { it.first to it.second }.toSet()
        fillBorder(6, used)
    }
    builtMenu.open(player)
}
