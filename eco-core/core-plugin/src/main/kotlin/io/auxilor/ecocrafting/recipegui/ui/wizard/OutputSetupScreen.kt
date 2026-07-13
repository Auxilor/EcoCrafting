package io.auxilor.ecocrafting.recipegui.ui.wizard

import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.util.formatEco
import io.auxilor.ecocrafting.recipegui.service.WizardState
import io.auxilor.ecocrafting.recipegui.ui.RecipeCreatorGUI
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

internal fun RecipeCreatorGUI.openOutputSetup(
    player: Player,
    typeKey: String,
    parts: Map<Int, ItemStack>,
    shapeless: Boolean,
    symmetry: Boolean,
    supportCrafter: Boolean,
    initialOutput: ItemStack?,
    editingId: String?,
    editingPermission: String?,
    initialState: WizardState? = null
) {
    val outputSlot = 3 to 4
    val backSlot = 6 to 3

    val builtMenu = menu(6) {
        title = "&8New Recipe - Output"

        allowChangingHeldItem()

        setSlot(
            outputSlot.first, outputSlot.second,
            (initialOutput?.let { Slot.builder(it) } ?: Slot.builder()).setCaptive().build()
        )

        setSlot(backSlot.first, backSlot.second, Slot.builder(
            ItemStackBuilder(Material.RED_DYE).setDisplayName("&c← Back".formatEco()).build()
        ).onLeftClick { event, _ ->
            val outputRawSlot = (outputSlot.first - 1) * 9 + (outputSlot.second - 1)
            val currentOutput = event.inventory.getItem(outputRawSlot)?.takeIf { !it.type.isAir }
            openIngredientSetup(
                player, typeKey, parts, shapeless, symmetry, supportCrafter,
                currentOutput, editingId, editingPermission, initialState
            )
        }.build())

        setSlot(6, 5, Slot.builder(
            ItemStackBuilder(Material.LIME_DYE).setDisplayName("&aNext →".formatEco()).build()
        ).onLeftClick { event, _ ->
            val outputRawSlot = (outputSlot.first - 1) * 9 + (outputSlot.second - 1)
            val outputItem = event.inventory.getItem(outputRawSlot)
                ?.takeIf { !it.type.isAir } ?: run {
                player.sendMessage("&cPlace an output item first.".formatEco())
                return@onLeftClick
            }
            player.closeInventory()
            val newState = WizardState(
                typeKey, parts, outputItem, shapeless, symmetry, supportCrafter, editingId, editingPermission
            )
            initialState?.let {
                newState.cookTime = it.cookTime
                newState.experience = it.experience
                newState.profession = it.profession
                newState.minLevel = it.minLevel
                newState.chance = it.chance
                newState.wanderingTrader = it.wanderingTrader
                newState.villagerXp = it.villagerXp
                newState.permission = it.permission
                newState.category = it.category
                newState.lockedByDefault = it.lockedByDefault
                newState.showWhenLocked = it.showWhenLocked
                newState.repairCost = it.repairCost
                newState.brewTime = it.brewTime
            }
            openOptions(player, newState)
        }.build())

        addWorkstationIcons(typeKey)
        val used = setOf(
            outputSlot,
            backSlot,
            6 to 5
        ) + workstationIconPositions.map { it.first to it.second }.toSet()
        fillBorder(6, used)
    }
    builtMenu.open(player)
}
