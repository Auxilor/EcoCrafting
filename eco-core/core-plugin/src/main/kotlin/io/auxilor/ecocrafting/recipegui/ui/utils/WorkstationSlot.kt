package io.auxilor.ecocrafting.recipegui.ui.utils

import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import io.auxilor.ecocrafting.recipe.model.RecipeDisplayType
import io.auxilor.ecocrafting.recipegui.ui.MARKER_CONFIG_KEY
import io.auxilor.ecocrafting.recipegui.ui.RecipeGUI
import io.auxilor.ecocrafting.recipegui.ui.WORKSTATION_MARKERS
import io.auxilor.ecocrafting.recipegui.ui.WorkstationMarkerState
import io.auxilor.ecocrafting.recipegui.ui.workstationMarkerState
import org.bukkit.entity.Player

fun RecipeGUIContext.buildWorkstationSlot(
    marker: Char,
    currentType: RecipeDisplayType?,
    currentTypes: Set<RecipeDisplayType>
): Slot = with(this) {
    val markerTypes = WORKSTATION_MARKERS[marker]!!
    val configKey = MARKER_CONFIG_KEY[marker]!!
    val workstationName = services.plugin.langYml.getString("workstation-names.$configKey")
    val state = workstationMarkerState(markerTypes, currentType, currentTypes)
    val stateKey = state.name.lowercase().replace("_", "-")

    val rawItem = services.plugin.configYml.getString("workstation-markers.$configKey.$stateKey")
    val lore = services.plugin.configYml.getFormattedStrings("workstation-markers.$configKey.lore.$stateKey")
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
                RecipeGUI(services, stack, effectiveAlternatives, targetIndex).open(event.whoClicked as Player, parent)
                sound("workstation-switch")?.playTo(event.whoClicked as Player)
            }
        }
    }
    slotBuilder.build()
}
