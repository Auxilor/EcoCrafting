package io.auxilor.ecocrafting.gui

import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.menu.MenuBuilder
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.recipe.parts.EmptyTestableItem
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.willfp.eco.util.formatEco
import io.auxilor.ecocrafting.plugin
import io.auxilor.ecocrafting.recipe.IngredientMatcher
import io.auxilor.ecocrafting.recipe.RecipeDisplayType
import io.auxilor.ecocrafting.recipe.RecipeIngredient
import io.auxilor.ecocrafting.recipe.RecipeSource
import io.auxilor.ecocrafting.recipe.ResolvedRecipe
import java.io.File
import java.util.UUID
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object RecipeCreatorGUI {

    // Same column/row layout and item/lore config as RecipeGUI's workstation-marker
    // column (workstation-markers.<key>.active/inactive + lang.yml workstation-names),
    // just laid out for the wizard's fixed 6-row menus instead of driven by mask.pattern.
    private val workstationIconPositions = listOf(
        Triple(1, 8, "crafting_table"), Triple(1, 9, "furnace"),
        Triple(2, 8, "blast_furnace"),  Triple(2, 9, "smoker"),
        Triple(3, 8, "campfire"),       Triple(3, 9, "brewing_stand"),
        Triple(4, 8, "smithing_table"), Triple(4, 9, "stonecutter"),
        Triple(5, 8, "grindstone"),     Triple(5, 9, "anvil"),
        Triple(6, 9, "villager")
    )

    private fun MenuBuilder.addWorkstationIcons(currentTypeKey: String) {
        workstationIconPositions.forEach { (row, col, key) ->
            val workstationName = plugin.langYml.getString("workstation-names.$key")
            val state = if (key == currentTypeKey) "active" else "inactive"
            val rawItem = plugin.configYml.getString("workstation-markers.$key.$state")
            val lore = plugin.configYml.getFormattedStrings("workstation-markers.$key.lore.$state")
                .map { it.replace("%workstation%", workstationName) }
            val item = ItemStackBuilder(Items.lookup(rawItem.replace("%workstation%", workstationName)))
                .addLoreLines(lore).build()
            val slotBuilder = Slot.builder(item)
            if (state == "inactive") {
                slotBuilder.onLeftClick { event, _ ->
                    // Open the new menu directly instead of closing first: Bukkit swaps
                    // the open inventory for the player in one step, whereas an explicit
                    // closeInventory() + open() on the next tick was snapping the cursor
                    // to the centre of the screen between the two.
                    openIngredientSetup(event.whoClicked as Player, key)
                }
            }
            setSlot(row, col, slotBuilder.build())
        }
    }

    private fun MenuBuilder.fillBorder(rows: Int, usedCells: Set<Pair<Int, Int>>) {
        val filler = ItemStackBuilder(Material.BLACK_STAINED_GLASS_PANE).setDisplayName("").build()
        for (row in 1..rows) {
            for (col in 1..9) {
                if ((row to col) !in usedCells) setSlot(row, col, Slot.builder(filler).build())
            }
        }
    }

    private val stationTypes = listOf(
        "crafting_table" to Material.CRAFTING_TABLE,
        "furnace"        to Material.FURNACE,
        "blast_furnace"  to Material.BLAST_FURNACE,
        "smoker"         to Material.SMOKER,
        "campfire"       to Material.CAMPFIRE,
        "smithing_table" to Material.SMITHING_TABLE,
        "stonecutter"    to Material.STONECUTTER,
        "brewing_stand"  to Material.BREWING_STAND,
        "grindstone"     to Material.GRINDSTONE,
        "anvil"          to Material.ANVIL,
        "villager"       to Material.EMERALD
    )

    val stationTypeKeys: List<String> = stationTypes.map { it.first }

    private val villagerProfessionKeys: List<String> by lazy {
        Registry.VILLAGER_PROFESSION.mapNotNull { it.key.key }.sorted()
    }

    fun startWizard(player: Player, typeKey: String?) {
        if (typeKey != null && typeKey in stationTypeKeys) {
            openIngredientSetup(player, typeKey)
        } else {
            openTypeSelect(player)
        }
    }

    fun existingRecipeIds(): List<String> {
        val dir = File(plugin.dataFolder, "recipes")
        if (!dir.exists()) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile && it.extension.equals("yml", ignoreCase = true) }
            .map { it.nameWithoutExtension }
            .toList()
    }

    private fun findRecipeFile(id: String): File? {
        val dir = File(plugin.dataFolder, "recipes")
        if (!dir.exists()) return null
        return dir.walkTopDown()
            .firstOrNull { it.isFile && it.extension.equals("yml", ignoreCase = true) && it.nameWithoutExtension.equals(id, ignoreCase = true) }
    }

    fun startEditWizard(player: Player, id: String) {
        val file = findRecipeFile(id) ?: run {
            player.sendMessage("&cNo recipe found with ID '$id'.".formatEco())
            return
        }
        val yaml = YamlConfiguration.loadConfiguration(file)
        val typeKey = yaml.getString("type")?.lowercase() ?: run {
            player.sendMessage("&cRecipe '$id' has no type set.".formatEco())
            return
        }

        fun lookupOrNull(raw: String?): ItemStack? {
            if (raw.isNullOrBlank() || raw == "air" || raw == "\"\"") return null
            val testable = Items.lookup(raw)
            return if (testable is EmptyTestableItem) null else testable.item.clone()
        }

        val parts = mutableMapOf<Int, ItemStack>()
        var output: ItemStack? = null
        var shapeless = false
        var symmetry = false

        when (typeKey) {
            "crafting_table", "crafter" -> {
                shapeless = yaml.getBoolean("shapeless")
                symmetry = yaml.getBoolean("symmetry")
                yaml.getStringList("recipe").forEachIndexed { index, raw ->
                    lookupOrNull(raw)?.let { parts[index] = it }
                }
            }
            "furnace", "blast_furnace", "smoker", "campfire" -> {
                lookupOrNull(yaml.getString("input"))?.let { parts[0] = it }
            }
            "smithing_table" -> {
                lookupOrNull(yaml.getString("template"))?.let { parts[0] = it }
                lookupOrNull(yaml.getString("base"))?.let { parts[1] = it }
                lookupOrNull(yaml.getString("addition"))?.let { parts[2] = it }
            }
            "stonecutter" -> {
                lookupOrNull(yaml.getString("input"))?.let { parts[0] = it }
                output = lookupOrNull(yaml.getConfigurationSection("outputs.0")?.getString("item"))
            }
            "brewing_stand" -> {
                lookupOrNull(yaml.getString("base"))?.let { parts[0] = it }
                lookupOrNull(yaml.getString("ingredient"))?.let { parts[1] = it }
            }
            "grindstone" -> {
                lookupOrNull(yaml.getString("item1"))?.let { parts[0] = it }
                lookupOrNull(yaml.getString("item2"))?.let { parts[1] = it }
            }
            "villager" -> {
                lookupOrNull(yaml.getString("input1"))?.let { parts[0] = it }
                lookupOrNull(yaml.getString("input2"))?.let { parts[1] = it }
            }
            "anvil" -> {
                lookupOrNull(yaml.getString("base"))?.let { parts[0] = it }
                lookupOrNull(yaml.getString("material"))?.let { parts[1] = it }
            }
        }

        if (typeKey != "stonecutter") {
            output = lookupOrNull(yaml.getString("output"))
        }

        val supportCrafter = yaml.getBoolean("support-crafter")
        val permission = yaml.getString("permission")
        val category = yaml.getString("category") ?: ""
        val lockedByDefault = yaml.getBoolean("locked-by-default")
        val showWhenLocked = yaml.getBoolean("show-when-locked")
        val repairCost = if (typeKey == "anvil") yaml.getInt("repair-cost", 1) else 1
        val brewTime = if (typeKey == "brewing_stand" && yaml.contains("brew-time")) yaml.getInt("brew-time") else null
        val cookTime = if (typeKey in setOf("furnace", "blast_furnace", "smoker", "campfire") && yaml.contains("cook-time")) yaml.getInt("cook-time") else null
        val experience = if (typeKey in setOf("furnace", "blast_furnace", "smoker", "campfire")) yaml.getDouble("experience") else 0.0
        val profession = if (typeKey == "villager") yaml.getString("profession")?.lowercase() ?: "" else ""
        val minLevel = if (typeKey == "villager") yaml.getInt("min-level") else 0
        val chance = if (typeKey == "villager") { if (yaml.contains("chance")) yaml.getDouble("chance") else 1.0 } else 1.0
        val wanderingTrader = if (typeKey == "villager") yaml.getBoolean("wandering-trader") else false
        val villagerXp = if (typeKey == "villager") yaml.getInt("villager-xp") else 0

        val seedState = WizardState(typeKey, parts, output ?: ItemStack(Material.AIR), shapeless, symmetry, supportCrafter, id, permission)
        seedState.category = category
        seedState.lockedByDefault = lockedByDefault
        seedState.showWhenLocked = showWhenLocked
        seedState.repairCost = repairCost
        seedState.brewTime = brewTime
        seedState.cookTime = cookTime
        seedState.experience = experience
        seedState.profession = profession
        seedState.minLevel = minLevel
        seedState.chance = chance
        seedState.wanderingTrader = wanderingTrader
        seedState.villagerXp = villagerXp

        openIngredientSetup(
            player,
            typeKey,
            initialParts = parts,
            initialShapeless = shapeless,
            initialSymmetry = symmetry,
            initialSupportCrafter = supportCrafter,
            initialOutput = output,
            editingId = id,
            editingPermission = permission,
            initialState = seedState
        )
    }

    fun openTypeSelect(player: Player) {
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

    private fun openIngredientSetup(
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

    // Uses eco's own lookup serialization so custom items (not just their base
    // Material) round-trip through CustomRecipeLoader.parseIngredient/parseOutputItem.
    private fun itemLookup(item: ItemStack?): String {
        if (item == null || item.type.isAir) return "air"
        return Items.toLookupString(item)
    }

    // Centred within the usable rows 1-6 / cols 1-7 area (cols 8-9 are the
    // workstation icon column), leaving row 6 col 5 clear for the Next button.
    private fun ingredientSlotLayout(typeKey: String): List<Pair<Int, Int>> = when (typeKey) {
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

    private fun openOutputSetup(
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
                openMetadata(player, newState)
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

    private fun openMetadata(player: Player, state: WizardState) {
        openOptions(player, state)
    }

    private fun buildOptionItem(icon: Material, label: String, value: String, isDefault: Boolean): ItemStack {
        val status = if (isDefault) "&7Current: &f$value &8(default)" else "&7Current: &f$value"
        return ItemStackBuilder(icon)
            .setDisplayName("&e$label".formatEco())
            .addLoreLines(listOf(status))
            .build()
    }

    private fun openOptions(player: Player, state: WizardState) {
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

    private fun promptPermissionValue(player: Player, state: WizardState) {
        player.sendMessage(
            "&aType a permission node, or &enone &ato clear it:".formatEco()
        )
        awaitingInput[player.uniqueId] = { input ->
            val trimmed = input.trim()
            state.permission = when {
                trimmed.equals("none", ignoreCase = true) -> ""
                trimmed.equals("default", ignoreCase = true) -> ""
                else -> trimmed
            }
            openOptions(player, state)
        }
    }

    private fun promptCategory(player: Player, state: WizardState) {
        player.sendMessage(
            "&aType a category ID, or &enone &ato clear it:".formatEco()
        )
        awaitingInput[player.uniqueId] = { input ->
            val trimmed = input.trim()
            state.category = when {
                trimmed.equals("none", ignoreCase = true) -> ""
                trimmed.equals("default", ignoreCase = true) -> ""
                else -> trimmed
            }
            openOptions(player, state)
        }
    }

    private fun promptCookTime(player: Player, state: WizardState) {
        player.sendMessage("&aType the cook time in ticks (e.g. 200), or &enone &afor the default:".formatEco())
        awaitingInput[player.uniqueId] = handler@{ input ->
            val trimmed = input.trim()
            if (trimmed.equals("none", ignoreCase = true) || trimmed.equals("default", ignoreCase = true)) {
                state.cookTime = null
                openOptions(player, state)
                return@handler
            }
            val ticks = trimmed.toIntOrNull()
            if (ticks == null || ticks <= 0) {
                player.sendMessage("&cInvalid cook time, try again.".formatEco())
                promptCookTime(player, state)
                return@handler
            }
            state.cookTime = ticks
            openOptions(player, state)
        }
    }

    private fun promptRepairCost(player: Player, state: WizardState) {
        player.sendMessage("&aType the repair cost in levels (e.g. 3), or &enone &afor 1:".formatEco())
        awaitingInput[player.uniqueId] = handler@{ input ->
            val trimmed = input.trim()
            if (trimmed.equals("none", ignoreCase = true) || trimmed.equals("default", ignoreCase = true)) {
                state.repairCost = 1
                openOptions(player, state)
                return@handler
            }
            val cost = trimmed.toIntOrNull()
            if (cost == null || cost < 0) {
                player.sendMessage("&cInvalid repair cost, try again.".formatEco())
                promptRepairCost(player, state)
                return@handler
            }
            state.repairCost = cost
            openOptions(player, state)
        }
    }

    private fun promptBrewTime(player: Player, state: WizardState) {
        player.sendMessage("&aType the brew time in ticks (e.g. 400), or &enone &afor the default:".formatEco())
        awaitingInput[player.uniqueId] = handler@{ input ->
            val trimmed = input.trim()
            if (trimmed.equals("none", ignoreCase = true) || trimmed.equals("default", ignoreCase = true)) {
                state.brewTime = null
                openOptions(player, state)
                return@handler
            }
            val ticks = trimmed.toIntOrNull()
            if (ticks == null || ticks <= 0) {
                player.sendMessage("&cInvalid brew time, try again.".formatEco())
                promptBrewTime(player, state)
                return@handler
            }
            state.brewTime = ticks
            openOptions(player, state)
        }
    }

    private fun promptExperience(player: Player, state: WizardState) {
        player.sendMessage("&aType the XP given per craft (e.g. 0.35), or &enone &afor 0:".formatEco())
        awaitingInput[player.uniqueId] = handler@{ input ->
            val trimmed = input.trim()
            if (trimmed.equals("none", ignoreCase = true) || trimmed.equals("default", ignoreCase = true)) {
                state.experience = 0.0
                openOptions(player, state)
                return@handler
            }
            val xp = trimmed.toDoubleOrNull()
            if (xp == null || xp < 0) {
                player.sendMessage("&cInvalid XP value, try again.".formatEco())
                promptExperience(player, state)
                return@handler
            }
            state.experience = xp
            openOptions(player, state)
        }
    }

    private fun promptProfession(player: Player, state: WizardState) {
        player.sendMessage(
            "&aType a villager profession (${villagerProfessionKeys.joinToString(", ")}), or &enone &afor any:".formatEco()
        )
        awaitingInput[player.uniqueId] = handler@{ input ->
            val trimmed = input.trim().lowercase()
            if (trimmed == "none" || trimmed == "default") {
                state.profession = ""
                openOptions(player, state)
                return@handler
            }
            if (trimmed !in villagerProfessionKeys) {
                player.sendMessage("&cUnknown profession, try again.".formatEco())
                promptProfession(player, state)
                return@handler
            }
            state.profession = trimmed
            openOptions(player, state)
        }
    }

    private fun promptMinLevel(player: Player, state: WizardState) {
        player.sendMessage("&aType the minimum villager level required (1-5), or &enone &afor 0:".formatEco())
        awaitingInput[player.uniqueId] = handler@{ input ->
            val trimmed = input.trim()
            if (trimmed.equals("none", ignoreCase = true) || trimmed.equals("default", ignoreCase = true)) {
                state.minLevel = 0
                openOptions(player, state)
                return@handler
            }
            val level = trimmed.toIntOrNull()
            if (level == null || level < 0) {
                player.sendMessage("&cInvalid level, try again.".formatEco())
                promptMinLevel(player, state)
                return@handler
            }
            state.minLevel = level
            openOptions(player, state)
        }
    }

    private fun promptChance(player: Player, state: WizardState) {
        player.sendMessage("&aType the trade chance (0.0-1.0), or &enone &afor 1.0:".formatEco())
        awaitingInput[player.uniqueId] = handler@{ input ->
            val trimmed = input.trim()
            if (trimmed.equals("none", ignoreCase = true) || trimmed.equals("default", ignoreCase = true)) {
                state.chance = 1.0
                openOptions(player, state)
                return@handler
            }
            val chance = trimmed.toDoubleOrNull()
            if (chance == null || chance < 0.0 || chance > 1.0) {
                player.sendMessage("&cInvalid chance, try again.".formatEco())
                promptChance(player, state)
                return@handler
            }
            state.chance = chance
            openOptions(player, state)
        }
    }

    private fun promptVillagerXp(player: Player, state: WizardState) {
        player.sendMessage("&aType the XP given to the villager per trade, or &enone &afor 0:".formatEco())
        awaitingInput[player.uniqueId] = handler@{ input ->
            val trimmed = input.trim()
            if (trimmed.equals("none", ignoreCase = true) || trimmed.equals("default", ignoreCase = true)) {
                state.villagerXp = 0
                openOptions(player, state)
                return@handler
            }
            val xp = trimmed.toIntOrNull()
            if (xp == null || xp < 0) {
                player.sendMessage("&cInvalid XP value, try again.".formatEco())
                promptVillagerXp(player, state)
                return@handler
            }
            state.villagerXp = xp
            openOptions(player, state)
        }
    }

    private fun promptId(player: Player, state: WizardState) {
        val existingId = state.editingId
        if (existingId != null) {
            openPreview(player, state.toPendingRecipe(existingId))
            return
        }
        player.sendMessage("&aType the recipe ID (lowercase, no spaces) in chat, or &ccancel &ato abort.".formatEco())
        awaitingInput[player.uniqueId] = handler@{ id ->
            val cleanId = id.lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_]"), "")
            if (cleanId.isBlank()) {
                player.sendMessage("&cID cannot be blank.".formatEco())
                promptId(player, state)
                return@handler
            }
            if (WorkstationRecipes.getByKey(NamespacedKey("ecocrafting", cleanId)) != null) {
                player.sendMessage("&cRecipe '$cleanId' already exists.".formatEco())
                promptId(player, state)
                return@handler
            }
            openPreview(player, state.toPendingRecipe(cleanId))
        }
    }

    fun openPreview(player: Player, pendingRecipe: PendingRecipe) {
        RecipeGUI(pendingRecipe.output, listOf(pendingRecipe.toPreviewResolvedRecipe())).open(player, null)
        player.sendMessage("&aPreview shown. Type &e/EcoCrafting confirm &ato save, or &c/EcoCrafting cancel &ato discard.".formatEco())
        pendingConfirm[player.uniqueId] = pendingRecipe
    }

    private val pendingConfirm = mutableMapOf<UUID, PendingRecipe>()
    val awaitingInput = mutableMapOf<UUID, (String) -> Unit>()

    fun confirmSave(player: Player) {
        val pending = pendingConfirm.remove(player.uniqueId) ?: run {
            player.sendMessage("&cNo pending recipe to confirm.".formatEco())
            return
        }
        saveRecipeYaml(pending)
        plugin.reload()
        player.sendMessage("&aRecipe '${pending.id}' saved and loaded.".formatEco())
    }

    fun cancelSave(player: Player) {
        pendingConfirm.remove(player.uniqueId)
        awaitingInput.remove(player.uniqueId)
        player.sendMessage("&cRecipe creation cancelled.".formatEco())
    }

    private fun saveRecipeYaml(pending: PendingRecipe) {
        val dir = File(plugin.dataFolder, "recipes")
        dir.mkdirs()
        val file = findRecipeFile(pending.id) ?: File(dir, "${pending.id}.yml")
        val yaml = StringBuilder()
        yaml.appendLine("type: ${pending.typeKey}")
        if (pending.category.isNotBlank()) yaml.appendLine("category: ${pending.category}")

        when (pending.typeKey) {
            "crafting_table", "crafter" -> {
                yaml.appendLine("shapeless: ${pending.shapeless}")
                yaml.appendLine("symmetry: ${pending.symmetry}")
                yaml.appendLine("support-crafter: ${pending.supportCrafter}")
                yaml.appendLine("recipe:")
                for (slot in 0..8) {
                    val item = pending.parts[slot]
                    val lookup = if (item == null || item.type.isAir) "\"\"" else itemLookup(item)
                    yaml.appendLine("  - $lookup")
                }
            }
            "furnace", "blast_furnace", "smoker", "campfire" -> {
                yaml.appendLine("input: ${itemLookup(pending.parts[0])}")
                pending.cookTime?.let { yaml.appendLine("cook-time: $it") }
                yaml.appendLine("experience: ${pending.experience}")
            }
            "smithing_table" -> {
                yaml.appendLine("template: ${itemLookup(pending.parts[0])}")
                yaml.appendLine("base: ${itemLookup(pending.parts[1])}")
                yaml.appendLine("addition: ${itemLookup(pending.parts[2])}")
            }
            "stonecutter" -> {
                yaml.appendLine("input: ${itemLookup(pending.parts[0])}")
                yaml.appendLine("outputs:")
                yaml.appendLine("  - item: ${itemLookup(pending.output)}")
                yaml.appendLine("    lore: []")
            }
            "brewing_stand" -> {
                yaml.appendLine("base: ${itemLookup(pending.parts[0])}")
                yaml.appendLine("ingredient: ${itemLookup(pending.parts[1])}")
                pending.brewTime?.let { yaml.appendLine("brew-time: $it") }
            }
            "grindstone" -> {
                yaml.appendLine("item1: ${itemLookup(pending.parts[0])}")
                pending.parts[1]?.let { yaml.appendLine("item2: ${itemLookup(it)}") }
            }
            "villager" -> {
                yaml.appendLine("input1: ${itemLookup(pending.parts[0])}")
                pending.parts[1]?.let { yaml.appendLine("input2: ${itemLookup(it)}") }
                if (pending.profession.isNotBlank()) yaml.appendLine("profession: ${pending.profession}")
                yaml.appendLine("min-level: ${pending.minLevel}")
                yaml.appendLine("chance: ${pending.chance}")
                yaml.appendLine("wandering-trader: ${pending.wanderingTrader}")
                yaml.appendLine("villager-xp: ${pending.villagerXp}")
            }
            "anvil" -> {
                yaml.appendLine("base: ${itemLookup(pending.parts[0])}")
                pending.parts[1]?.let { yaml.appendLine("material: ${itemLookup(it)}") }
                yaml.appendLine("repair-cost: ${pending.repairCost}")
            }
        }

        if (pending.typeKey != "stonecutter") {
            yaml.appendLine("output: ${itemLookup(pending.output)}")
            yaml.appendLine("lore: []")
        }

        if (pending.permission.isNotBlank()) yaml.appendLine("permission: \"${pending.permission}\"")
        yaml.appendLine("locked-by-default: ${pending.lockedByDefault}")
        yaml.appendLine("show-when-locked: ${pending.showWhenLocked}")
        yaml.appendLine("visibility-conditions: []")
        yaml.appendLine("crafting-conditions: []")
        yaml.appendLine("unlock-conditions: []")

        file.writeText(yaml.toString())
    }
}

private class WizardState(
    val typeKey: String,
    val parts: Map<Int, ItemStack>,
    val output: ItemStack,
    initialShapeless: Boolean,
    initialSymmetry: Boolean,
    initialSupportCrafter: Boolean,
    val editingId: String?,
    val editingPermission: String?
) {
    var shapeless: Boolean = initialShapeless
    var symmetry: Boolean = initialSymmetry
    var supportCrafter: Boolean = initialSupportCrafter
    var cookTime: Int? = null
    var experience: Double = 0.0
    var profession: String = ""
    var minLevel: Int = 0
    var chance: Double = 1.0
    var wanderingTrader: Boolean = false
    var villagerXp: Int = 0
    var permission: String = editingPermission ?: ""
    var category: String = ""
    var lockedByDefault: Boolean = false
    var showWhenLocked: Boolean = false
    var repairCost: Int = 1
    var brewTime: Int? = null

    fun toPendingRecipe(id: String) = PendingRecipe(
        typeKey = typeKey,
        parts = parts,
        output = output,
        shapeless = shapeless,
        symmetry = symmetry,
        supportCrafter = supportCrafter,
        cookTime = cookTime,
        experience = experience,
        profession = profession,
        minLevel = minLevel,
        chance = chance,
        wanderingTrader = wanderingTrader,
        villagerXp = villagerXp,
        id = id,
        permission = permission,
        category = category,
        lockedByDefault = lockedByDefault,
        showWhenLocked = showWhenLocked,
        repairCost = repairCost,
        brewTime = brewTime
    )
}

data class PendingRecipe(
    val typeKey: String,
    val parts: Map<Int, ItemStack>,
    val output: ItemStack,
    val shapeless: Boolean,
    val symmetry: Boolean,
    val supportCrafter: Boolean,
    val cookTime: Int?,
    val experience: Double,
    val profession: String,
    val minLevel: Int,
    val chance: Double,
    val wanderingTrader: Boolean,
    val villagerXp: Int,
    val id: String,
    val permission: String,
    val category: String,
    val lockedByDefault: Boolean,
    val showWhenLocked: Boolean,
    val repairCost: Int,
    val brewTime: Int?
)

private fun PendingRecipe.toPreviewResolvedRecipe(): ResolvedRecipe {
    fun ingredient(item: ItemStack?): RecipeIngredient =
        if (item == null || item.type.isAir) RecipeIngredient.empty(ItemStack(Material.AIR))
        else RecipeIngredient(item.clone(), IngredientMatcher.SimilarItem(item.clone()))

    val ingredients: List<RecipeIngredient> = if (typeKey == "crafting_table" || typeKey == "crafter") {
        (0..8).map { ingredient(parts[it]) }
    } else {
        val ordered = parts.entries.sortedBy { it.key }.map { ingredient(it.value) }
        (ordered + List(9) { ingredient(null) }).take(9)
    }

    val displayType = when (typeKey) {
        "crafting_table"  -> RecipeDisplayType.CRAFTING
        "crafter"         -> RecipeDisplayType.CRAFTER
        "furnace"         -> RecipeDisplayType.SMELTING
        "blast_furnace"   -> RecipeDisplayType.BLAST_FURNACE
        "smoker"          -> RecipeDisplayType.SMOKER
        "campfire"        -> RecipeDisplayType.CAMPFIRE
        "smithing_table"  -> RecipeDisplayType.SMITHING
        "stonecutter"     -> RecipeDisplayType.STONECUTTER
        "brewing_stand"   -> RecipeDisplayType.BREWING
        "grindstone"      -> RecipeDisplayType.GRINDSTONE
        "anvil"           -> RecipeDisplayType.ANVIL
        "villager"        -> RecipeDisplayType.VILLAGER
        else              -> RecipeDisplayType.CRAFTING
    }

    return ResolvedRecipe(
        key = null,
        output = output.clone(),
        ingredients = ingredients,
        permission = permission.takeIf { it.isNotBlank() },
        source = RecipeSource.CUSTOM,
        shapeless = shapeless,
        displayType = displayType,
        cookTime = cookTime,
        villagerXp = villagerXp.takeIf { it > 0 }
    )
}
