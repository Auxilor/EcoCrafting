package com.exanthiax.ecocrafting.recipegui.ui.wizard

import com.exanthiax.ecocrafting.recipegui.service.PendingRecipe
import com.exanthiax.ecocrafting.recipegui.ui.RecipeCreatorGUI
import org.bukkit.Material
import org.bukkit.entity.Player
import wiki.ecohub.game.item.builder.ItemStackBuilder
import wiki.ecohub.modules.menus.menu
import wiki.ecohub.modules.menus.slot.Slot
import wiki.ecohub.ui.text.formatEco

internal fun RecipeCreatorGUI.openConfirmPreview(player: Player, pending: PendingRecipe) {
    val slotLayout = ingredientSlotLayout(pending.typeKey)
    val outputSlot = 3 to 4
    val cancelSlot = 6 to 3
    val confirmSlot = 6 to 5

    val builtMenu = menu(6) {
        setTitle("&8New Recipe - Confirm")

        slotLayout.forEachIndexed { index, (row, col) ->
            pending.parts[index]?.let { setSlot(row = row, column = col, slot = Slot.builder(it.clone()).build()) }
        }
        setSlot(row = outputSlot.first, column = outputSlot.second, slot = Slot.builder(pending.output.clone()).build())

        setSlot(row = cancelSlot.first, column = cancelSlot.second, slot = Slot.builder(
            ItemStackBuilder(Material.RED_WOOL).setDisplayName("&c✗ Cancel".formatEco()).build()
        ).onLeftClick { _, _ ->
            player.closeInventory()
            cancelSave(player)
        }.build())

        setSlot(row = confirmSlot.first, column = confirmSlot.second, slot = Slot.builder(
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
