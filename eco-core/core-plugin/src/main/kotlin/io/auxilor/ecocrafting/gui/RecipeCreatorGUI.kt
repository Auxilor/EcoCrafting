package io.auxilor.ecocrafting.gui

import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.MenuBuilder
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.recipe.parts.EmptyTestableItem
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.willfp.eco.util.formatEco
import io.auxilor.ecocrafting.plugin
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

        openIngredientSetup(
            player,
            typeKey,
            initialParts = parts,
            initialShapeless = shapeless,
            initialSymmetry = symmetry,
            initialSupportCrafter = supportCrafter,
            initialOutput = output,
            editingId = id,
            editingPermission = permission
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
        editingPermission: String? = null
    ) {
        val slotLayout = ingredientSlotLayout(typeKey)
        var shapeless = initialShapeless
        var symmetry = initialSymmetry
        var supportCrafter = initialSupportCrafter
        val shapelessSlot = 6 to 8
        val symmetrySlot = 1 to 3
        val supportCrafterSlot = 1 to 5

        val builtMenu = menu(6) {
            title = "&8New Recipe - Ingredients"

            allowChangingHeldItem()

            slotLayout.forEachIndexed { index, (row, col) ->
                val slotBuilder = initialParts[index]?.let { Slot.builder(it) } ?: Slot.builder()
                setSlot(row, col, slotBuilder.setCaptive().build())
            }

            if (typeKey == "crafting_table") {
                setSlot(shapelessSlot.first, shapelessSlot.second, Slot.builder(
                    ItemStackBuilder(Material.PAPER).setDisplayName(
                        ("&e" + (if (shapeless) "Shapeless" else "Shaped") + " (click to toggle)").formatEco()
                    ).build()
                ).onLeftClick { event, _ ->
                    shapeless = !shapeless
                    val label = (if (shapeless) "&eShapeless" else "&eShaped").formatEco()
                    event.inventory.setItem(event.rawSlot, ItemStackBuilder(Material.PAPER).setDisplayName(label).build())
                }.build())

                setSlot(symmetrySlot.first, symmetrySlot.second, Slot.builder(
                    ItemStackBuilder(Material.COMPASS).setDisplayName(
                        ("&e" + (if (symmetry) "Symmetry: ON" else "Symmetry: OFF") + " (click to toggle)").formatEco()
                    ).build()
                ).onLeftClick { event, _ ->
                    symmetry = !symmetry
                    val label = ("&e" + (if (symmetry) "Symmetry: ON" else "Symmetry: OFF")).formatEco()
                    event.inventory.setItem(event.rawSlot, ItemStackBuilder(Material.COMPASS).setDisplayName(label).build())
                }.build())

                setSlot(supportCrafterSlot.first, supportCrafterSlot.second, Slot.builder(
                    ItemStackBuilder(Material.CRAFTER).setDisplayName(
                        ("&e" + (if (supportCrafter) "Crafter Support: ON" else "Crafter Support: OFF") + " (click to toggle)").formatEco()
                    ).build()
                ).onLeftClick { event, _ ->
                    supportCrafter = !supportCrafter
                    val label = ("&e" + (if (supportCrafter) "Crafter Support: ON" else "Crafter Support: OFF")).formatEco()
                    event.inventory.setItem(event.rawSlot, ItemStackBuilder(Material.CRAFTER).setDisplayName(label).build())
                }.build())
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
                    player, typeKey, collectedParts, shapeless, symmetry, supportCrafter,
                    initialOutput, editingId, editingPermission
                )
            }.build())

            addWorkstationIcons(typeKey)
            val used = slotLayout.toSet() + setOf(6 to 5) +
                (if (typeKey == "crafting_table") setOf(shapelessSlot, symmetrySlot, supportCrafterSlot) else emptySet()) +
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
        editingPermission: String?
    ) {
        val outputSlot = 3 to 4

        val builtMenu = menu(6) {
            title = "&8New Recipe - Output"

            allowChangingHeldItem()

            setSlot(
                outputSlot.first, outputSlot.second,
                (initialOutput?.let { Slot.builder(it) } ?: Slot.builder()).setCaptive().build()
            )

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
                openMetadata(
                    player,
                    WizardState(typeKey, parts, outputItem, shapeless, symmetry, supportCrafter, editingId, editingPermission)
                )
            }.build())

            addWorkstationIcons(typeKey)
            val used = setOf(
                outputSlot,
                6 to 5
            ) + workstationIconPositions.map { it.first to it.second }.toSet()
            fillBorder(6, used)
        }
        builtMenu.open(player)
    }

    private fun openMetadata(player: Player, state: WizardState) {
        when (state.typeKey) {
            "furnace", "blast_furnace", "smoker", "campfire" -> promptCookTime(player, state)
            "villager" -> promptProfession(player, state)
            else -> promptId(player, state)
        }
    }

    private fun promptCookTime(player: Player, state: WizardState) {
        player.sendMessage("&aType the cook time in ticks (e.g. 200), or &enone &afor the default:".formatEco())
        awaitingInput[player.uniqueId] = handler@{ input ->
            val trimmed = input.trim()
            if (!trimmed.equals("none", ignoreCase = true)) {
                val ticks = trimmed.toIntOrNull()
                if (ticks == null || ticks <= 0) {
                    player.sendMessage("&cInvalid cook time, try again.".formatEco())
                    promptCookTime(player, state)
                    return@handler
                }
                state.cookTime = ticks
            }
            promptExperience(player, state)
        }
    }

    private fun promptExperience(player: Player, state: WizardState) {
        player.sendMessage("&aType the XP given per craft (e.g. 0.35), or &enone &afor 0:".formatEco())
        awaitingInput[player.uniqueId] = handler@{ input ->
            val trimmed = input.trim()
            if (!trimmed.equals("none", ignoreCase = true)) {
                val xp = trimmed.toDoubleOrNull()
                if (xp == null || xp < 0) {
                    player.sendMessage("&cInvalid XP value, try again.".formatEco())
                    promptExperience(player, state)
                    return@handler
                }
                state.experience = xp
            }
            promptId(player, state)
        }
    }

    private fun promptProfession(player: Player, state: WizardState) {
        player.sendMessage(
            "&aType a villager profession (${villagerProfessionKeys.joinToString(", ")}), or &enone &afor any:".formatEco()
        )
        awaitingInput[player.uniqueId] = handler@{ input ->
            val trimmed = input.trim().lowercase()
            if (trimmed != "none") {
                if (trimmed !in villagerProfessionKeys) {
                    player.sendMessage("&cUnknown profession, try again.".formatEco())
                    promptProfession(player, state)
                    return@handler
                }
                state.profession = trimmed
            }
            promptMinLevel(player, state)
        }
    }

    private fun promptMinLevel(player: Player, state: WizardState) {
        player.sendMessage("&aType the minimum villager level required (1-5), or &enone &afor 0:".formatEco())
        awaitingInput[player.uniqueId] = handler@{ input ->
            val trimmed = input.trim()
            if (!trimmed.equals("none", ignoreCase = true)) {
                val level = trimmed.toIntOrNull()
                if (level == null || level < 0) {
                    player.sendMessage("&cInvalid level, try again.".formatEco())
                    promptMinLevel(player, state)
                    return@handler
                }
                state.minLevel = level
            }
            promptChance(player, state)
        }
    }

    private fun promptChance(player: Player, state: WizardState) {
        player.sendMessage("&aType the trade chance (0.0-1.0), or &enone &afor 1.0:".formatEco())
        awaitingInput[player.uniqueId] = handler@{ input ->
            val trimmed = input.trim()
            if (!trimmed.equals("none", ignoreCase = true)) {
                val chance = trimmed.toDoubleOrNull()
                if (chance == null || chance < 0.0 || chance > 1.0) {
                    player.sendMessage("&cInvalid chance, try again.".formatEco())
                    promptChance(player, state)
                    return@handler
                }
                state.chance = chance
            }
            promptWanderingTrader(player, state)
        }
    }

    private fun promptWanderingTrader(player: Player, state: WizardState) {
        player.sendMessage("&aShould this trade apply to wandering traders? Type &eyes &aor &eno:".formatEco())
        awaitingInput[player.uniqueId] = handler@{ input ->
            when (input.trim().lowercase()) {
                "yes", "y", "true" -> state.wanderingTrader = true
                "no", "n", "false" -> state.wanderingTrader = false
                else -> {
                    player.sendMessage("&cType 'yes' or 'no'.".formatEco())
                    promptWanderingTrader(player, state)
                    return@handler
                }
            }
            promptVillagerXp(player, state)
        }
    }

    private fun promptVillagerXp(player: Player, state: WizardState) {
        player.sendMessage("&aType the XP given to the villager per trade, or &enone &afor 0:".formatEco())
        awaitingInput[player.uniqueId] = handler@{ input ->
            val trimmed = input.trim()
            if (!trimmed.equals("none", ignoreCase = true)) {
                val xp = trimmed.toIntOrNull()
                if (xp == null || xp < 0) {
                    player.sendMessage("&cInvalid XP value, try again.".formatEco())
                    promptVillagerXp(player, state)
                    return@handler
                }
                state.villagerXp = xp
            }
            promptId(player, state)
        }
    }

    private fun promptId(player: Player, state: WizardState) {
        val existingId = state.editingId
        if (existingId != null) {
            promptPermission(player, state, existingId)
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
            promptPermission(player, state, cleanId)
        }
    }

    private fun promptPermission(player: Player, state: WizardState, id: String) {
        val currentPermission = state.editingPermission?.takeIf { it.isNotBlank() }
        player.sendMessage(
            (
                if (currentPermission != null)
                    "&aType a permission node, &ekeep &ato retain '$currentPermission', or &enone &ato clear it:"
                else
                    "&aType a permission node, or &enone &ato skip:"
                ).formatEco()
        )
        awaitingInput[player.uniqueId] = { perm ->
            val trimmed = perm.trim()
            val cleanPerm = when {
                trimmed.equals("none", ignoreCase = true) -> ""
                trimmed.equals("keep", ignoreCase = true) -> currentPermission ?: ""
                else -> trimmed
            }
            openPreview(player, state.toPendingRecipe(id, cleanPerm))
        }
    }

    fun openPreview(player: Player, pendingRecipe: PendingRecipe) {
        RecipeGUI(pendingRecipe.output).open(player, null)
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
                yaml.appendLine("repair-cost: 1")
            }
        }

        if (pending.typeKey != "stonecutter") {
            yaml.appendLine("output: ${itemLookup(pending.output)}")
            yaml.appendLine("lore: []")
        }

        if (pending.permission.isNotBlank()) yaml.appendLine("permission: \"${pending.permission}\"")
        yaml.appendLine("locked-by-default: false")
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
    val shapeless: Boolean,
    val symmetry: Boolean,
    val supportCrafter: Boolean,
    val editingId: String?,
    val editingPermission: String?
) {
    var cookTime: Int? = null
    var experience: Double = 0.0
    var profession: String = ""
    var minLevel: Int = 0
    var chance: Double = 1.0
    var wanderingTrader: Boolean = false
    var villagerXp: Int = 0

    fun toPendingRecipe(id: String, permission: String) = PendingRecipe(
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
        permission = permission
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
    val permission: String
)
