package ru.oftendev.recipebook.gui.utils

import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import org.bukkit.entity.Player
import ru.oftendev.recipebook.gui.MARKER_CONFIG_KEY
import ru.oftendev.recipebook.gui.RecipeGUI
import ru.oftendev.recipebook.gui.WORKSTATION_MARKERS
import ru.oftendev.recipebook.gui.WorkstationMarkerState
import ru.oftendev.recipebook.gui.workstationMarkerState
import ru.oftendev.recipebook.recipe.RecipeDisplayType
import ru.oftendev.recipebook.recipeBookPlugin

fun RecipeGUIContext.buildWorkstationSlot(
    marker: Char,
    currentType: RecipeDisplayType?,
    currentTypes: Set<RecipeDisplayType>
): Slot = with(this) {
    val markerTypes = WORKSTATION_MARKERS[marker]!!
    val configKey = MARKER_CONFIG_KEY[marker]!!
    val wsName = recipeBookPlugin.langYml.getString("workstation-names.$configKey")
    val state = workstationMarkerState(markerTypes, currentType, currentTypes)
    val stateKey = state.name.lowercase().replace("_", "-")

    val rawItem = recipeBookPlugin.configYml.getString("workstation-markers.$configKey.$stateKey")
    val lore = recipeBookPlugin.configYml.getFormattedStrings("workstation-markers.$configKey.lore.$stateKey")
        .map { it.replace("%workstation%", wsName) }
    val item = ItemStackBuilder(Items.lookup(rawItem.replace("%workstation%", wsName)))
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
