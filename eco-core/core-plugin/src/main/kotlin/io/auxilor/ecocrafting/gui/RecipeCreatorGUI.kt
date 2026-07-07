package io.auxilor.ecocrafting.gui

import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.MenuBuilder
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.willfp.eco.util.formatEco
import io.auxilor.ecocrafting.plugin
import java.util.UUID
import org.bukkit.Material
import org.bukkit.NamespacedKey
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

    fun startWizard(player: Player, typeKey: String?) {
        if (typeKey != null && typeKey in stationTypeKeys) {
            openIngredientSetup(player, typeKey)
        } else {
            openTypeSelect(player)
        }
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

    private fun openIngredientSetup(player: Player, typeKey: String) {
        val slotLayout = ingredientSlotLayout(typeKey)
        var shapeless = false
        val shapelessSlot = 6 to 8

        val builtMenu = menu(6) {
            title = "&8New Recipe - Ingredients"

            allowChangingHeldItem()

            slotLayout.forEach { (row, col) ->
                setSlot(row, col, Slot.builder().setCaptive().build())
            }

            if (typeKey == "crafting_table") {
                setSlot(shapelessSlot.first, shapelessSlot.second, Slot.builder(
                    ItemStackBuilder(Material.PAPER).setDisplayName("&eShaped (click to toggle)".formatEco()).build()
                ).onLeftClick { event, _ ->
                    shapeless = !shapeless
                    val label = (if (shapeless) "&eShapeless" else "&eShaped").formatEco()
                    event.inventory.setItem(event.rawSlot, ItemStackBuilder(Material.PAPER).setDisplayName(label).build())
                }.build())
            }

            setSlot(6, 5, Slot.builder(
                ItemStackBuilder(Material.LIME_DYE).setDisplayName("&aNext →".formatEco()).build()
            ).onLeftClick { event, _ ->
                val collectedParts = mutableMapOf<Int, ItemStack>()
                slotLayout.forEachIndexed { index, (row, col) ->
                    val rawSlot = (row - 1) * 9 + (col - 1)
                    event.inventory.getItem(rawSlot)?.takeIf { !it.type.isAir }?.let {
                        collectedParts[index] = it.clone().apply { amount = 1 }
                    }
                }
                openOutputSetup(player, typeKey, collectedParts, shapeless)
            }.build())

            addWorkstationIcons(typeKey)
            val used = slotLayout.toSet() + setOf(6 to 5) +
                (if (typeKey == "crafting_table") setOf(shapelessSlot) else emptySet()) +
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

    fun openOutputSetup(player: Player, typeKey: String, parts: Map<Int, ItemStack>, shapeless: Boolean) {
        var ghost = false
        val outputSlot = 3 to 4
        val ghostSlot = 3 to 6

        val builtMenu = menu(6) {
            title = "&8New Recipe - Output"

            allowChangingHeldItem()

            setSlot(outputSlot.first, outputSlot.second, Slot.builder().setCaptive().build())

            setSlot(ghostSlot.first, ghostSlot.second, Slot.builder(
                ItemStackBuilder(Material.GRAY_DYE).setDisplayName("&7Ghost: OFF".formatEco()).build()
            ).onLeftClick { event, _ ->
                ghost = !ghost
                val label = (if (ghost) "&aGhost: ON" else "&7Ghost: OFF").formatEco()
                val material = if (ghost) Material.LIME_DYE else Material.GRAY_DYE
                event.inventory.setItem(event.rawSlot, ItemStackBuilder(material).setDisplayName(label).build())
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
                openMetadata(player, typeKey, parts, outputItem, ghost, shapeless)
            }.build())

            addWorkstationIcons(typeKey)
            val used = setOf(
                outputSlot,
                ghostSlot,
                6 to 5
            ) + workstationIconPositions.map { it.first to it.second }.toSet()
            fillBorder(6, used)
        }
        builtMenu.open(player)
    }

    fun openMetadata(
        player: Player,
        typeKey: String,
        parts: Map<Int, ItemStack>,
        output: ItemStack,
        ghost: Boolean,
        shapeless: Boolean
    ) {
        // Prompt for recipe ID via chat
        player.sendMessage("&aType the recipe ID (lowercase, no spaces) in chat, or &ccancel &ato abort.".formatEco())
        awaitingInput[player.uniqueId] = handler@{ id ->
            val cleanId = id.lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_]"), "")
            if (cleanId.isBlank()) {
                player.sendMessage("&cID cannot be blank.".formatEco())
                return@handler
            }
            if (WorkstationRecipes.getByKey(NamespacedKey("ecocrafting", cleanId)) != null) {
                player.sendMessage("&cRecipe '$cleanId' already exists.".formatEco())
                return@handler
            }
            // A blank chat message never reaches the server (the client doesn't send an
            // empty packet), so "none" is the actual way to skip the permission prompt.
            player.sendMessage("&aType a permission node, or &enone &ato skip:".formatEco())
            awaitingInput[player.uniqueId] = { perm ->
                val cleanPerm = perm.trim().takeUnless { it.equals("none", ignoreCase = true) } ?: ""
                openPreview(player, PendingRecipe(typeKey, parts, output, ghost, shapeless, cleanId, cleanPerm))
            }
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
        val dir = java.io.File(plugin.dataFolder, "recipes")
        dir.mkdirs()
        val file = java.io.File(dir, "${pending.id}.yml")
        val yaml = StringBuilder()
        yaml.appendLine("type: ${pending.typeKey}")

        when (pending.typeKey) {
            "crafting_table", "crafter" -> {
                yaml.appendLine("shapeless: ${pending.shapeless}")
                yaml.appendLine("recipe:")
                for (slot in 0..8) {
                    val item = pending.parts[slot]
                    val lookup = if (item == null || item.type.isAir) "\"\"" else itemLookup(item)
                    yaml.appendLine("  - $lookup")
                }
            }
            "furnace", "blast_furnace", "smoker", "campfire" -> {
                yaml.appendLine("input: ${itemLookup(pending.parts[0])}")
                yaml.appendLine("cook-time: 200")
                yaml.appendLine("experience: 0.0")
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
                yaml.appendLine("    give-result-item: ${!pending.ghost}")
                if (pending.ghost) { yaml.appendLine("    effects: []"); yaml.appendLine("    conditions: []") }
            }
            "brewing_stand" -> {
                yaml.appendLine("base: ${itemLookup(pending.parts[0])}")
                yaml.appendLine("ingredient: ${itemLookup(pending.parts[1])}")
            }
            "grindstone", "villager" -> {
                yaml.appendLine("input1: ${itemLookup(pending.parts[0])}")
                pending.parts[1]?.let { yaml.appendLine("input2: ${itemLookup(it)}") }
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
            yaml.appendLine("give-result-item: ${!pending.ghost}")
            if (pending.ghost) {
                yaml.appendLine("effects: []")
                yaml.appendLine("conditions: []")
            }
        }

        if (pending.permission.isNotBlank()) yaml.appendLine("permission: \"${pending.permission}\"")
        yaml.appendLine("locked-by-default: false")
        yaml.appendLine("visibility-conditions: []")
        yaml.appendLine("crafting-conditions: []")
        yaml.appendLine("unlock-conditions: []")

        file.writeText(yaml.toString())
    }
}

data class PendingRecipe(
    val typeKey: String,
    val parts: Map<Int, ItemStack>,
    val output: ItemStack,
    val ghost: Boolean,
    val shapeless: Boolean,
    val id: String,
    val permission: String
)
