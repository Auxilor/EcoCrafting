package com.exanthiax.ecocrafting.recipegui.ui.wizard

import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.util.formatEco
import com.exanthiax.ecocrafting.recipegui.service.WizardState
import com.exanthiax.ecocrafting.recipegui.ui.RecipeCreatorGUI
import org.bukkit.Material
import org.bukkit.entity.Player

internal fun buildOptionItem(icon: Material, label: String, value: String, isDefault: Boolean) =
    ItemStackBuilder(icon)
        .setDisplayName("&e$label".formatEco())
        .addLoreLines(listOf(if (isDefault) "&7Current: &f$value &8(default)" else "&7Current: &f$value"))
        .build()

internal fun RecipeCreatorGUI.openOptions(player: Player, state: WizardState) {
    val backSlot = 6 to 3
    val confirmSlot = 6 to 5

    val builtMenu = menu(6) {
        title = "&8New Recipe - Options"

        var fieldIndex = 0
        val fieldSlots = mutableSetOf<Pair<Int, Int>>()
        fun nextFieldSlot(): Pair<Int, Int> {
            val row = 1 + fieldIndex / 6
            val col = 2 + fieldIndex % 6
            fieldIndex++
            return row to col
        }

        val permSlot = nextFieldSlot()
        fieldSlots += permSlot
        setSlot(permSlot.first, permSlot.second, Slot.builder { _: Player, _: Menu ->
            buildOptionItem(
                Material.NAME_TAG, "Permission",
                state.permission.ifBlank { "none" }, state.permission.isBlank()
            )
        }.onLeftClick { _, _ ->
            player.closeInventory()
            promptPermissionValue(player, state)
        }.onRightClick { _, _ ->
            state.permission = ""
        }.build())

        val categorySlot = nextFieldSlot()
        fieldSlots += categorySlot
        setSlot(categorySlot.first, categorySlot.second, Slot.builder { _: Player, _: Menu ->
            buildOptionItem(
                Material.OAK_SIGN, "Category",
                state.category.ifBlank { "none" }, state.category.isBlank()
            )
        }.onLeftClick { _, _ ->
            player.closeInventory()
            promptCategory(player, state)
        }.onRightClick { _, _ ->
            state.category = ""
        }.build())

        val lockedByDefaultSlot = nextFieldSlot()
        fieldSlots += lockedByDefaultSlot
        setSlot(lockedByDefaultSlot.first, lockedByDefaultSlot.second, Slot.builder { _: Player, _: Menu ->
            ItemStackBuilder(Material.IRON_DOOR).setDisplayName(
                ("&e" + (if (state.lockedByDefault) "Locked by Default: ON" else "Locked by Default: OFF") + " (click to toggle)").formatEco()
            ).build()
        }.onLeftClick { _, _ ->
            state.lockedByDefault = !state.lockedByDefault
        }.build())

        val showWhenLockedSlot = nextFieldSlot()
        fieldSlots += showWhenLockedSlot
        setSlot(showWhenLockedSlot.first, showWhenLockedSlot.second, Slot.builder { _: Player, _: Menu ->
            ItemStackBuilder(Material.SPYGLASS).setDisplayName(
                ("&e" + (if (state.showWhenLocked) "Show When Locked: ON" else "Show When Locked: OFF") + " (click to toggle)").formatEco()
            ).build()
        }.onLeftClick { _, _ ->
            state.showWhenLocked = !state.showWhenLocked
        }.build())

        if (state.typeKey == "crafting_table") {
            val shapelessSlot = nextFieldSlot()
            fieldSlots += shapelessSlot
            setSlot(shapelessSlot.first, shapelessSlot.second, Slot.builder { _: Player, _: Menu ->
                ItemStackBuilder(Material.PAPER).setDisplayName(
                    ("&e" + (if (state.shapeless) "Shapeless" else "Shaped") + " (click to toggle)").formatEco()
                ).build()
            }.onLeftClick { _, _ ->
                state.shapeless = !state.shapeless
            }.build())

            val symmetrySlot = nextFieldSlot()
            fieldSlots += symmetrySlot
            setSlot(symmetrySlot.first, symmetrySlot.second, Slot.builder { _: Player, _: Menu ->
                ItemStackBuilder(Material.COMPASS).setDisplayName(
                    ("&e" + (if (state.symmetry) "Symmetry: ON" else "Symmetry: OFF") + " (click to toggle)").formatEco()
                ).build()
            }.onLeftClick { _, _ ->
                state.symmetry = !state.symmetry
            }.build())

            val supportCrafterSlot = nextFieldSlot()
            fieldSlots += supportCrafterSlot
            setSlot(supportCrafterSlot.first, supportCrafterSlot.second, Slot.builder { _: Player, _: Menu ->
                ItemStackBuilder(Material.CRAFTER).setDisplayName(
                    ("&e" + (if (state.supportCrafter) "Crafter Support: ON" else "Crafter Support: OFF") + " (click to toggle)").formatEco()
                ).build()
            }.onLeftClick { _, _ ->
                state.supportCrafter = !state.supportCrafter
            }.build())
        }

        if (state.typeKey in setOf("furnace", "blast_furnace", "smoker", "campfire")) {
            val cookTimeSlot = nextFieldSlot()
            fieldSlots += cookTimeSlot
            setSlot(cookTimeSlot.first, cookTimeSlot.second, Slot.builder { _: Player, _: Menu ->
                buildOptionItem(
                    Material.CLOCK, "Cook Time",
                    state.cookTime?.let { "$it ticks" } ?: "server default",
                    state.cookTime == null
                )
            }.onLeftClick { _, _ ->
                player.closeInventory()
                promptCookTime(player, state)
            }.onRightClick { _, _ ->
                state.cookTime = null
            }.build())

            val xpSlot = nextFieldSlot()
            fieldSlots += xpSlot
            setSlot(xpSlot.first, xpSlot.second, Slot.builder { _: Player, _: Menu ->
                buildOptionItem(Material.EXPERIENCE_BOTTLE, "XP", "${state.experience}", state.experience == 0.0)
            }.onLeftClick { _, _ ->
                player.closeInventory()
                promptExperience(player, state)
            }.onRightClick { _, _ ->
                state.experience = 0.0
            }.build())
        }

        if (state.typeKey == "anvil") {
            val repairCostSlot = nextFieldSlot()
            fieldSlots += repairCostSlot
            setSlot(repairCostSlot.first, repairCostSlot.second, Slot.builder { _: Player, _: Menu ->
                buildOptionItem(Material.EXPERIENCE_BOTTLE, "Repair Cost", "${state.repairCost}", state.repairCost == 1)
            }.onLeftClick { _, _ ->
                player.closeInventory()
                promptRepairCost(player, state)
            }.onRightClick { _, _ ->
                state.repairCost = 1
            }.build())
        }

        if (state.typeKey == "brewing_stand") {
            val brewTimeSlot = nextFieldSlot()
            fieldSlots += brewTimeSlot
            setSlot(brewTimeSlot.first, brewTimeSlot.second, Slot.builder { _: Player, _: Menu ->
                buildOptionItem(
                    Material.CLOCK, "Brew Time",
                    state.brewTime?.let { "$it ticks" } ?: "server default",
                    state.brewTime == null
                )
            }.onLeftClick { _, _ ->
                player.closeInventory()
                promptBrewTime(player, state)
            }.onRightClick { _, _ ->
                state.brewTime = null
            }.build())
        }

        if (state.typeKey == "villager") {
            val professionSlot = nextFieldSlot()
            fieldSlots += professionSlot
            setSlot(professionSlot.first, professionSlot.second, Slot.builder { _: Player, _: Menu ->
                buildOptionItem(
                    Material.VILLAGER_SPAWN_EGG, "Profession",
                    state.profession.ifBlank { "any" }, state.profession.isBlank()
                )
            }.onLeftClick { _, _ ->
                player.closeInventory()
                promptProfession(player, state)
            }.onRightClick { _, _ ->
                state.profession = ""
            }.build())

            val minLevelSlot = nextFieldSlot()
            fieldSlots += minLevelSlot
            setSlot(minLevelSlot.first, minLevelSlot.second, Slot.builder { _: Player, _: Menu ->
                buildOptionItem(Material.BOOK, "Min Level", "${state.minLevel}", state.minLevel == 0)
            }.onLeftClick { _, _ ->
                player.closeInventory()
                promptMinLevel(player, state)
            }.onRightClick { _, _ ->
                state.minLevel = 0
            }.build())

            val chanceSlot = nextFieldSlot()
            fieldSlots += chanceSlot
            setSlot(chanceSlot.first, chanceSlot.second, Slot.builder { _: Player, _: Menu ->
                buildOptionItem(Material.REDSTONE, "Chance", "${state.chance}", state.chance == 1.0)
            }.onLeftClick { _, _ ->
                player.closeInventory()
                promptChance(player, state)
            }.onRightClick { _, _ ->
                state.chance = 1.0
            }.build())

            val traderSlot = nextFieldSlot()
            fieldSlots += traderSlot
            setSlot(traderSlot.first, traderSlot.second, Slot.builder { _: Player, _: Menu ->
                buildOptionItem(
                    Material.LEAD, "Wandering Trader",
                    if (state.wanderingTrader) "yes" else "no", !state.wanderingTrader
                )
            }.onLeftClick { _, _ ->
                state.wanderingTrader = !state.wanderingTrader
            }.build())

            val villagerXpSlot = nextFieldSlot()
            fieldSlots += villagerXpSlot
            setSlot(villagerXpSlot.first, villagerXpSlot.second, Slot.builder { _: Player, _: Menu ->
                buildOptionItem(Material.EXPERIENCE_BOTTLE, "Villager XP", "${state.villagerXp}", state.villagerXp == 0)
            }.onLeftClick { _, _ ->
                player.closeInventory()
                promptVillagerXp(player, state)
            }.onRightClick { _, _ ->
                state.villagerXp = 0
            }.build())
        }

        setSlot(backSlot.first, backSlot.second, Slot.builder(
            ItemStackBuilder(Material.RED_DYE).setDisplayName("&c← Back".formatEco()).build()
        ).onLeftClick { _, _ ->
            openOutputSetup(
                player, state.typeKey, state.parts, state.shapeless, state.symmetry,
                state.supportCrafter, state.output, state.editingId, state.editingPermission,
                initialState = state
            )
        }.build())

        setSlot(confirmSlot.first, confirmSlot.second, Slot.builder(
            ItemStackBuilder(Material.LIME_DYE).setDisplayName("&aConfirm →".formatEco()).build()
        ).onLeftClick { _, _ ->
            player.closeInventory()
            promptId(player, state)
        }.build())

        addWorkstationIcons(state.typeKey)
        val used = fieldSlots + setOf(backSlot, confirmSlot) +
            workstationIconPositions.map { it.first to it.second }.toSet()
        fillBorder(6, used)
    }
    builtMenu.open(player)
}
