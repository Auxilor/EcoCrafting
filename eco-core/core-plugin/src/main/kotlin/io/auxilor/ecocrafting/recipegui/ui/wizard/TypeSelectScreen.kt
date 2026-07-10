package io.auxilor.ecocrafting.recipegui.ui.wizard

import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.builder.ItemStackBuilder
import io.auxilor.ecocrafting.recipegui.ui.RecipeCreatorGUI
import org.bukkit.entity.Player

internal fun RecipeCreatorGUI.openTypeSelect(player: Player) {
    val builtMenu = menu(2) {
        title = "&8New Recipe - Choose Type"

        stationTypes.forEachIndexed { index, (typeKey, material) ->
            val row = (index / 9) + 1
            val col = (index % 9) + 1
            val displayName = "&a" + plugin.langYml.getString("workstation-names.$typeKey")
            setSlot(row, col, Slot.builder(
                ItemStackBuilder(material).setDisplayName(displayName).build()
            ).onLeftClick { _, _ ->
                openIngredientSetup(player, typeKey)
            }.build())
        }

    }
    builtMenu.open(player)
}
