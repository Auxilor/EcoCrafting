package com.exanthiax.ecocrafting.recipegui.ui.wizard

import com.exanthiax.ecocrafting.recipegui.ui.RecipeCreatorGUI
import org.bukkit.entity.Player
import wiki.ecohub.game.item.builder.ItemStackBuilder
import wiki.ecohub.modules.menus.menu
import wiki.ecohub.modules.menus.slot.Slot

internal fun RecipeCreatorGUI.openTypeSelect(player: Player) {
    val builtMenu = menu(2) {
        setTitle("&8New Recipe - Choose Type")

        stationTypes.forEachIndexed { index, (typeKey, material) ->
            val row = (index / 9) + 1
            val col = (index % 9) + 1
            val displayName = "&a" + plugin.langYml.getString("workstation-names.$typeKey")
            setSlot(row = row, column = col, slot = Slot.builder(
                ItemStackBuilder(material).setDisplayName(displayName).build()
            ).onLeftClick { _, _ ->
                openIngredientSetup(player, typeKey)
            }.build())
        }

    }
    builtMenu.open(player)
}
