package com.exanthiax.ecocrafting.recipegui.ui.wizard

import com.exanthiax.ecocrafting.limit.model.UNLIMITED
import com.exanthiax.ecocrafting.recipegui.service.WizardState
import com.exanthiax.ecocrafting.recipegui.ui.RecipeCreatorGUI
import org.bukkit.Material
import org.bukkit.entity.Player
import wiki.ecohub.game.item.builder.ItemStackBuilder
import wiki.ecohub.modules.menus.menu
import wiki.ecohub.modules.menus.menu.Menu
import wiki.ecohub.modules.menus.slot.Slot
import wiki.ecohub.ui.text.formatEco

internal fun buildOptionItem(icon: Material, label: String, value: String, isDefault: Boolean) =
    ItemStackBuilder(icon)
        .setDisplayName("&e$label".formatEco())
        .addLoreLines(listOf(if (isDefault) "&7Current: &f$value &8(default)" else "&7Current: &f$value"))
        .build()

internal fun RecipeCreatorGUI.openOptions(player: Player, state: WizardState) {
    val backSlot = 6 to 3
    val confirmSlot = 6 to 5

    val builtMenu = menu(6) {
        setTitle("&8New Recipe - Options")

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
        setSlot(row = permSlot.first, column = permSlot.second, slot = Slot.builder { _: Player, _: Menu ->
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
        setSlot(row = categorySlot.first, column = categorySlot.second, slot = Slot.builder { _: Player, _: Menu ->
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
        setSlot(
            row = lockedByDefaultSlot.first,
            column = lockedByDefaultSlot.second,
            slot = Slot.builder { _: Player, _: Menu ->
                ItemStackBuilder(Material.IRON_DOOR).setDisplayName(
                    ("&e" + (if (state.lockedByDefault) "Locked by Default: ON" else "Locked by Default: OFF") + " (click to toggle)").formatEco()
                ).build()
            }.onLeftClick { _, _ ->
                state.lockedByDefault = !state.lockedByDefault
            }.build()
        )

        val showWhenLockedSlot = nextFieldSlot()
        fieldSlots += showWhenLockedSlot
        setSlot(
            row = showWhenLockedSlot.first,
            column = showWhenLockedSlot.second,
            slot = Slot.builder { _: Player, _: Menu ->
                ItemStackBuilder(Material.SPYGLASS).setDisplayName(
                    ("&e" + (if (state.showWhenLocked) "Show When Locked: ON" else "Show When Locked: OFF") + " (click to toggle)").formatEco()
                ).build()
            }.onLeftClick { _, _ ->
                state.showWhenLocked = !state.showWhenLocked
            }.build()
        )

        if (state.typeKey != "brewing_stand") {
            val playerLimitSlot = nextFieldSlot()
            fieldSlots += playerLimitSlot
            setSlot(
                row = playerLimitSlot.first,
                column = playerLimitSlot.second,
                slot = Slot.builder { _: Player, _: Menu ->
                    buildOptionItem(
                        Material.HOPPER, "Player Craft Limit",
                        state.playerCraftLimit.takeIf { it >= 0 }?.toString() ?: "unlimited",
                        state.playerCraftLimit < 0
                    )
                }.onLeftClick { _, _ ->
                    player.closeInventory()
                    promptPlayerCraftLimit(player, state)
                }.onRightClick { _, _ ->
                    state.playerCraftLimit = UNLIMITED
                }.build()
            )

            val globalLimitSlot = nextFieldSlot()
            fieldSlots += globalLimitSlot
            setSlot(
                row = globalLimitSlot.first,
                column = globalLimitSlot.second,
                slot = Slot.builder { _: Player, _: Menu ->
                    buildOptionItem(
                        Material.CHEST, "Global Craft Limit",
                        state.globalCraftLimit.takeIf { it >= 0 }?.toString() ?: "unlimited",
                        state.globalCraftLimit < 0
                    )
                }.onLeftClick { _, _ ->
                    player.closeInventory()
                    promptGlobalCraftLimit(player, state)
                }.onRightClick { _, _ ->
                    state.globalCraftLimit = UNLIMITED
                }.build()
            )
        }

        if (state.typeKey == "crafting_table") {
            val shapelessSlot = nextFieldSlot()
            fieldSlots += shapelessSlot
            setSlot(
                row = shapelessSlot.first,
                column = shapelessSlot.second,
                slot = Slot.builder { _: Player, _: Menu ->
                    ItemStackBuilder(Material.PAPER).setDisplayName(
                        ("&e" + (if (state.shapeless) "Shapeless" else "Shaped") + " (click to toggle)").formatEco()
                    ).build()
                }.onLeftClick { _, _ ->
                    state.shapeless = !state.shapeless
                }.build()
            )

            val symmetrySlot = nextFieldSlot()
            fieldSlots += symmetrySlot
            setSlot(row = symmetrySlot.first, column = symmetrySlot.second, slot = Slot.builder { _: Player, _: Menu ->
                ItemStackBuilder(Material.COMPASS).setDisplayName(
                    ("&e" + (if (state.symmetry) "Symmetry: ON" else "Symmetry: OFF") + " (click to toggle)").formatEco()
                ).build()
            }.onLeftClick { _, _ ->
                state.symmetry = !state.symmetry
            }.build())

            val supportCrafterSlot = nextFieldSlot()
            fieldSlots += supportCrafterSlot
            setSlot(
                row = supportCrafterSlot.first,
                column = supportCrafterSlot.second,
                slot = Slot.builder { _: Player, _: Menu ->
                    ItemStackBuilder(Material.CRAFTER).setDisplayName(
                        ("&e" + (if (state.supportCrafter) "Crafter Support: ON" else "Crafter Support: OFF") + " (click to toggle)").formatEco()
                    ).build()
                }.onLeftClick { _, _ ->
                    state.supportCrafter = !state.supportCrafter
                }.build()
            )
        }

        if (state.typeKey in setOf("furnace", "blast_furnace", "smoker", "campfire")) {
            val cookTimeSlot = nextFieldSlot()
            fieldSlots += cookTimeSlot
            setSlot(row = cookTimeSlot.first, column = cookTimeSlot.second, slot = Slot.builder { _: Player, _: Menu ->
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
            setSlot(row = xpSlot.first, column = xpSlot.second, slot = Slot.builder { _: Player, _: Menu ->
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
            setSlot(
                row = repairCostSlot.first,
                column = repairCostSlot.second,
                slot = Slot.builder { _: Player, _: Menu ->
                    buildOptionItem(
                        Material.EXPERIENCE_BOTTLE,
                        "Repair Cost",
                        "${state.repairCost}",
                        state.repairCost == 1
                    )
                }.onLeftClick { _, _ ->
                    player.closeInventory()
                    promptRepairCost(player, state)
                }.onRightClick { _, _ ->
                    state.repairCost = 1
                }.build()
            )
        }

        if (state.typeKey == "brewing_stand") {
            val brewTimeSlot = nextFieldSlot()
            fieldSlots += brewTimeSlot
            setSlot(row = brewTimeSlot.first, column = brewTimeSlot.second, slot = Slot.builder { _: Player, _: Menu ->
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
            setSlot(
                row = professionSlot.first,
                column = professionSlot.second,
                slot = Slot.builder { _: Player, _: Menu ->
                    buildOptionItem(
                        Material.VILLAGER_SPAWN_EGG, "Profession",
                        state.profession.ifBlank { "any" }, state.profession.isBlank()
                    )
                }.onLeftClick { _, _ ->
                    player.closeInventory()
                    promptProfession(player, state)
                }.onRightClick { _, _ ->
                    state.profession = ""
                }.build()
            )

            val minLevelSlot = nextFieldSlot()
            fieldSlots += minLevelSlot
            setSlot(row = minLevelSlot.first, column = minLevelSlot.second, slot = Slot.builder { _: Player, _: Menu ->
                buildOptionItem(Material.BOOK, "Min Level", "${state.minLevel}", state.minLevel == 0)
            }.onLeftClick { _, _ ->
                player.closeInventory()
                promptMinLevel(player, state)
            }.onRightClick { _, _ ->
                state.minLevel = 0
            }.build())

            val chanceSlot = nextFieldSlot()
            fieldSlots += chanceSlot
            setSlot(row = chanceSlot.first, column = chanceSlot.second, slot = Slot.builder { _: Player, _: Menu ->
                buildOptionItem(Material.REDSTONE, "Chance", "${state.chance}", state.chance == 100.0)
            }.onLeftClick { _, _ ->
                player.closeInventory()
                promptChance(player, state)
            }.onRightClick { _, _ ->
                state.chance = 100.0
            }.build())

            val traderSlot = nextFieldSlot()
            fieldSlots += traderSlot
            setSlot(row = traderSlot.first, column = traderSlot.second, slot = Slot.builder { _: Player, _: Menu ->
                buildOptionItem(
                    Material.LEAD, "Wandering Trader",
                    if (state.wanderingTrader) "yes" else "no", !state.wanderingTrader
                )
            }.onLeftClick { _, _ ->
                state.wanderingTrader = !state.wanderingTrader
            }.build())

            val villagerXpSlot = nextFieldSlot()
            fieldSlots += villagerXpSlot
            setSlot(
                row = villagerXpSlot.first,
                column = villagerXpSlot.second,
                slot = Slot.builder { _: Player, _: Menu ->
                    buildOptionItem(
                        Material.EXPERIENCE_BOTTLE,
                        "Villager XP",
                        "${state.villagerXp}",
                        state.villagerXp == 0
                    )
                }.onLeftClick { _, _ ->
                    player.closeInventory()
                    promptVillagerXp(player, state)
                }.onRightClick { _, _ ->
                    state.villagerXp = 0
                }.build()
            )
        }

        setSlot(row = backSlot.first, column = backSlot.second, slot = Slot.builder(
            ItemStackBuilder(Material.RED_DYE).setDisplayName("&c← Back".formatEco()).build()
        ).onLeftClick { _, _ ->
            openOutputSetup(
                player, state.typeKey, state.parts, state.shapeless, state.symmetry,
                state.supportCrafter, state.output, state.editingId, state.editingPermission,
                initialState = state
            )
        }.build())

        setSlot(row = confirmSlot.first, column = confirmSlot.second, slot = Slot.builder(
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
