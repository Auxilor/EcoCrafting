package io.auxilor.ecocrafting.gui.utils

import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import io.auxilor.ecocrafting.gui.MARKER_CONFIG_KEY
import io.auxilor.ecocrafting.gui.RecipeGUI
import io.auxilor.ecocrafting.gui.WORKSTATION_MARKERS
import io.auxilor.ecocrafting.gui.WorkstationMarkerState
import io.auxilor.ecocrafting.gui.workstationMarkerState
import io.auxilor.ecocrafting.plugin
import io.auxilor.ecocrafting.recipe.RecipeDisplayType
import org.bukkit.entity.Player

fun RecipeGUIContext.buildWorkstationSlot(
    marker: Char,
    currentType: RecipeDisplayType?,
    currentTypes: Set<RecipeDisplayType>
): Slot = with(this) {
    val markerTypes = WORKSTATION_MARKERS[marker]!!
    val configKey = MARKER_CONFIG_KEY[marker]!!
    val workstationName = plugin.langYml.getString("workstation-names.$configKey")
    val state = workstationMarkerState(markerTypes, currentType, currentTypes)
    val stateKey = state.name.lowercase().replace("_", "-")

    val rawItem = plugin.configYml.getString("workstation-markers.$configKey.$stateKey")
    val lore = plugin.configYml.getFormattedStrings("workstation-markers.$configKey.lore.$stateKey")
        .map { it.replace("%workstation%", workstationName) }
    val item = ItemStackBuilder(Items.lookup(rawItem.replace("%workstation%", workstationName)))
        .addLoreLines(lore)
        .withGlobalFlags()
        .build()

    val slotBuilder = Slot.builder(item)
    if (state == WorkstationMarkerState.INACTIVE) {
        val targetIndex = effectiveAlternatives.indexOfFirst { markerTypes.contains(it.displayType) }
        if (targetIndex >= 0) {
            slotBuilder.onLeftClick { event, _ ->
                RecipeGUI(stack, effectiveAlternatives, targetIndex).open(event.whoClicked as Player, parent)
                sound("workstation-switch")?.playTo(event.whoClicked as Player)
            }
        }
    }
    slotBuilder.build()
}
