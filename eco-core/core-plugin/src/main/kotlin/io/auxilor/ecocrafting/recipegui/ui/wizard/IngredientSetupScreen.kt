package io.auxilor.ecocrafting.recipegui.ui.wizard

import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.util.formatEco
import io.auxilor.ecocrafting.recipegui.service.WizardState
import io.auxilor.ecocrafting.recipegui.ui.RecipeCreatorGUI
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

// Centred within the usable rows 1-6 / cols 1-7 area (cols 8-9 are the
// workstation icon column), leaving row 6 col 5 clear for the Next button.
internal fun ingredientSlotLayout(typeKey: String): List<Pair<Int, Int>> = when (typeKey) {
    "crafting_table", "crafter" -> listOf(
        2 to 3, 2 to 4, 2 to 5,
        3 to 3, 3 to 4, 3 to 5,
        4 to 3, 4 to 4, 4 to 5
    )
    "furnace", "blast_furnace", "smoker", "campfire", "stonecutter" -> listOf(3 to 4)
    "smithing_table" -> listOf(
        3 to 2,
        3 to 4,
        3 to 6
    )
    "brewing_stand" -> listOf(
        3 to 3,
        3 to 5
    )
    "grindstone", "villager" -> listOf(
        3 to 3,
        3 to 5
    )
    "anvil" -> listOf(
        3 to 3,
        3 to 5
    )
    else -> emptyList()
}

internal fun RecipeCreatorGUI.openIngredientSetup(
    player: Player,
    typeKey: String,
    initialParts: Map<Int, ItemStack> = emptyMap(),
    initialShapeless: Boolean = false,
    initialSymmetry: Boolean = false,
    initialSupportCrafter: Boolean = false,
    initialOutput: ItemStack? = null,
    editingId: String? = null,
    editingPermission: String? = null,
    initialState: WizardState? = null
) {
    val slotLayout = ingredientSlotLayout(typeKey)

    val builtMenu = menu(6) {
        title = "&8New Recipe - Ingredients"

        allowChangingHeldItem()

        slotLayout.forEachIndexed { index, (row, col) ->
            val slotBuilder = initialParts[index]?.let { Slot.builder(it) } ?: Slot.builder()
            setSlot(row, col, slotBuilder.setCaptive().build())
        }

        setSlot(6, 5, Slot.builder(
            ItemStackBuilder(Material.LIME_DYE).setDisplayName("&aNext →".formatEco()).build()
        ).onLeftClick { event, _ ->
            val collectedParts = mutableMapOf<Int, ItemStack>()
            slotLayout.forEachIndexed { index, (row, col) ->
                val rawSlot = (row - 1) * 9 + (col - 1)
                event.inventory.getItem(rawSlot)?.takeIf { !it.type.isAir }?.let {
                    collectedParts[index] = it.clone()
                }
            }
            openOutputSetup(
                player, typeKey, collectedParts, initialShapeless, initialSymmetry, initialSupportCrafter,
                initialOutput, editingId, editingPermission, initialState
            )
        }.build())

        addWorkstationIcons(typeKey)
        val used = slotLayout.toSet() + setOf(6 to 5) +
            workstationIconPositions.map { it.first to it.second }.toSet()
        fillBorder(6, used)
    }
    builtMenu.open(player)
}
