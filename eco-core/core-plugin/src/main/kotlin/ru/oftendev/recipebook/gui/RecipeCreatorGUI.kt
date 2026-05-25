package ru.oftendev.recipebook.gui

import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.MaskItems
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.builder.ItemStackBuilder
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import ru.oftendev.recipebook.recipeBookPlugin
import java.util.UUID

object RecipeCreatorGUI {

    private val stationTypes = listOf(
        Triple("crafting_table",    Material.CRAFTING_TABLE,    "&aCrafting Table"),
        Triple("furnace",           Material.FURNACE,           "&aFurnace"),
        Triple("blast_furnace",     Material.BLAST_FURNACE,     "&aBlast Furnace"),
        Triple("smoker",            Material.SMOKER,            "&aSmoker"),
        Triple("campfire",          Material.CAMPFIRE,          "&aCampfire"),
        Triple("smithing_table",    Material.SMITHING_TABLE,    "&aSmithing Table"),
        Triple("stonecutter",       Material.STONECUTTER,       "&aStonecutter"),
        Triple("crafter",           Material.CRAFTER,           "&aCrafter"),
        Triple("brewing_stand",     Material.BREWING_STAND,     "&aBrewing Stand"),
        Triple("grindstone",        Material.GRINDSTONE,        "&aGrindstone"),
        Triple("anvil",             Material.ANVIL,             "&aAnvil"),
        Triple("villager",          Material.EMERALD,           "&aVillager Trade")
    )

    fun openTypeSelect(player: Player) {
        val builtMenu = menu(2) {
            title = "&8New Recipe — Choose Type"

            stationTypes.forEachIndexed { index, (typeKey, material, label) ->
                val row = (index / 9) + 1
                val col = (index % 9) + 1
                setSlot(row, col, Slot.builder(
                    ItemStackBuilder(material).setDisplayName(label).build()
                ).onLeftClick { _, _ ->
                    openIngredientSetup(player, typeKey)
                }.build())
            }

            setMask(FillerMask(
                MaskItems.fromItemNames(listOf("black_stained_glass_pane")),
                "000000000", "000000000"
            ))
        }
        builtMenu.open(player)
    }

    private fun openIngredientSetup(player: Player, typeKey: String) {
        val slotLayout = ingredientSlotLayout(typeKey)
        val collectedParts = mutableMapOf<Int, ItemStack>()

        val builtMenu = menu(4) {
            title = "&8New Recipe — Ingredients"

            slotLayout.forEachIndexed { index, (row, col) ->
                setSlot(row, col, Slot.builder(
                    ItemStackBuilder(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                        .setDisplayName("&7Slot ${index + 1} — place ingredient").build()
                ).onLeftClick { event, _ ->
                    val cursor = event.cursor ?: return@onLeftClick
                    if (cursor.type.isAir) return@onLeftClick
                    collectedParts[index] = cursor.clone().apply { amount = 1 }
                    event.inventory.setItem(event.rawSlot, ItemStackBuilder(cursor.clone()).build())
                }.onRightClick { event, _ ->
                    collectedParts.remove(index)
                    event.inventory.setItem(
                        event.rawSlot,
                        ItemStackBuilder(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                            .setDisplayName("&7Slot ${index + 1} — place ingredient").build()
                    )
                }.build())
            }

            if (typeKey == "crafting_table") {
                var shapeless = false
                setSlot(4, 9, Slot.builder(
                    ItemStackBuilder(Material.PAPER).setDisplayName("&eShaped (click to toggle)").build()
                ).onLeftClick { event, _ ->
                    shapeless = !shapeless
                    val label = if (shapeless) "&eShapeless" else "&eShaped"
                    event.inventory.setItem(event.rawSlot, ItemStackBuilder(Material.PAPER).setDisplayName(label).build())
                }.build())
            }

            setSlot(4, 5, Slot.builder(
                ItemStackBuilder(Material.LIME_DYE).setDisplayName("&aNext →").build()
            ).onLeftClick { _, _ ->
                player.closeInventory()
                openOutputSetup(player, typeKey, collectedParts)
            }.build())

            setMask(FillerMask(
                MaskItems.fromItemNames(listOf("black_stained_glass_pane")),
                "111111111", "111111111", "111111111", "111111111"
            ))
        }
        builtMenu.open(player)
    }

    private fun ingredientSlotLayout(typeKey: String): List<Pair<Int, Int>> = when (typeKey) {
        "crafting_table", "crafter" -> listOf(
            1 to 1, 1 to 2, 1 to 3,
            2 to 1, 2 to 2, 2 to 3,
            3 to 1, 3 to 2, 3 to 3
        )
        "furnace", "blast_furnace", "smoker", "campfire", "stonecutter" -> listOf(2 to 2)
        "smithing_table" -> listOf(2 to 2, 2 to 4, 2 to 6)
        "brewing_stand"  -> listOf(2 to 2, 2 to 4)
        "grindstone", "villager" -> listOf(2 to 2, 2 to 4)
        "anvil" -> listOf(2 to 2, 2 to 4)
        else -> emptyList()
    }

    fun openOutputSetup(player: Player, typeKey: String, parts: Map<Int, ItemStack>) {
        var ghost = false

        val builtMenu = menu(3) {
            title = "&8New Recipe — Output"

            setSlot(2, 5, Slot.builder(
                ItemStackBuilder(Material.LIGHT_GRAY_STAINED_GLASS_PANE).setDisplayName("&7Place output item").build()
            ).onLeftClick { event, _ ->
                val cursor = event.cursor ?: return@onLeftClick
                if (cursor.type.isAir) return@onLeftClick
                event.inventory.setItem(event.rawSlot, cursor.clone())
            }.build())

            setSlot(2, 7, Slot.builder(
                ItemStackBuilder(Material.GRAY_DYE).setDisplayName("&7Ghost: OFF").build()
            ).onLeftClick { event, _ ->
                ghost = !ghost
                val label = if (ghost) "&aGhost: ON" else "&7Ghost: OFF"
                val material = if (ghost) Material.LIME_DYE else Material.GRAY_DYE
                event.inventory.setItem(event.rawSlot, ItemStackBuilder(material).setDisplayName(label).build())
            }.build())

            setSlot(3, 5, Slot.builder(
                ItemStackBuilder(Material.LIME_DYE).setDisplayName("&aNext →").build()
            ).onLeftClick { event, _ ->
                val outputItem = event.inventory.getItem(13)
                    ?.takeIf { !it.type.isAir } ?: run {
                    player.sendMessage("&cPlace an output item first.")
                    return@onLeftClick
                }
                player.closeInventory()
                openMetadata(player, typeKey, parts, outputItem, ghost)
            }.build())

            setMask(FillerMask(
                MaskItems.fromItemNames(listOf("black_stained_glass_pane")),
                "111111111", "111111111", "111111111"
            ))
        }
        builtMenu.open(player)
    }

    fun openMetadata(
        player: Player,
        typeKey: String,
        parts: Map<Int, ItemStack>,
        output: ItemStack,
        ghost: Boolean
    ) {
        // Prompt for recipe ID via chat
        player.sendMessage("&aType the recipe ID (lowercase, no spaces) in chat, or &ccancel &ato abort.")
        awaitingInput[player.uniqueId] = handler@{ id ->
            val cleanId = id.lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_]"), "")
            if (cleanId.isBlank()) {
                player.sendMessage("&cID cannot be blank.")
                return@handler
            }
            if (WorkstationRecipes.getByKey(NamespacedKey("recipebook", cleanId)) != null) {
                player.sendMessage("&cRecipe '$cleanId' already exists.")
                return@handler
            }
            player.sendMessage("&aType a permission node (or leave blank and press enter):")
            awaitingInput[player.uniqueId] = { perm ->
                openPreview(player, PendingRecipe(typeKey, parts, output, ghost, cleanId, perm.trim()))
            }
        }
    }

    fun openPreview(player: Player, pendingRecipe: PendingRecipe) {
        RecipeGUI(pendingRecipe.output).open(player, null)
        player.sendMessage("&aPreview shown. Type &e/recipebook confirm &ato save, or &c/recipebook cancel &ato discard.")
        pendingConfirm[player.uniqueId] = pendingRecipe
    }

    private val pendingConfirm = mutableMapOf<UUID, PendingRecipe>()
    val awaitingInput = mutableMapOf<UUID, (String) -> Unit>()

    fun confirmSave(player: Player) {
        val pending = pendingConfirm.remove(player.uniqueId) ?: run {
            player.sendMessage("&cNo pending recipe to confirm.")
            return
        }
        saveRecipeYaml(pending)
        recipeBookPlugin.reload()
        player.sendMessage("&aRecipe '${pending.id}' saved and loaded.")
    }

    fun cancelSave(player: Player) {
        pendingConfirm.remove(player.uniqueId)
        awaitingInput.remove(player.uniqueId)
        player.sendMessage("&cRecipe creation cancelled.")
    }

    private fun saveRecipeYaml(pending: PendingRecipe) {
        val dir = java.io.File(recipeBookPlugin.dataFolder, "recipes")
        dir.mkdirs()
        val file = java.io.File(dir, "${pending.id}.yml")
        val yaml = StringBuilder()
        yaml.appendLine("type: ${pending.typeKey}")

        when (pending.typeKey) {
            "crafting_table", "crafter" -> {
                yaml.appendLine("shapeless: false")
                yaml.appendLine("recipe:")
                for (slot in 0..8) {
                    val item = pending.parts[slot]
                    val lookup = if (item == null || item.type.isAir) "\"\"" else item.type.name.lowercase()
                    yaml.appendLine("  - $lookup")
                }
            }
            "furnace", "blast_furnace", "smoker", "campfire" -> {
                val input = pending.parts[0]?.type?.name?.lowercase() ?: "air"
                yaml.appendLine("input: $input")
                yaml.appendLine("cook-time: 200")
                yaml.appendLine("experience: 0.0")
            }
            "smithing_table" -> {
                yaml.appendLine("template: ${pending.parts[0]?.type?.name?.lowercase() ?: "air"}")
                yaml.appendLine("base: ${pending.parts[1]?.type?.name?.lowercase() ?: "air"}")
                yaml.appendLine("addition: ${pending.parts[2]?.type?.name?.lowercase() ?: "air"}")
            }
            "stonecutter" -> {
                yaml.appendLine("input: ${pending.parts[0]?.type?.name?.lowercase() ?: "air"}")
                yaml.appendLine("outputs:")
                yaml.appendLine("  - item: ${pending.output.type.name.lowercase()}")
                yaml.appendLine("    lore: []")
                yaml.appendLine("    ghost: ${pending.ghost}")
                if (pending.ghost) { yaml.appendLine("    effects: []"); yaml.appendLine("    conditions: []") }
            }
            "brewing_stand" -> {
                yaml.appendLine("base: ${pending.parts[0]?.type?.name?.lowercase() ?: "air"}")
                yaml.appendLine("ingredient: ${pending.parts[1]?.type?.name?.lowercase() ?: "air"}")
            }
            "grindstone", "villager" -> {
                yaml.appendLine("input1: ${pending.parts[0]?.type?.name?.lowercase() ?: "air"}")
                pending.parts[1]?.let { yaml.appendLine("input2: ${it.type.name.lowercase()}") }
            }
            "anvil" -> {
                yaml.appendLine("base: ${pending.parts[0]?.type?.name?.lowercase() ?: "air"}")
                pending.parts[1]?.let { yaml.appendLine("material: ${it.type.name.lowercase()}") }
                yaml.appendLine("repair-cost: 1")
            }
        }

        if (pending.typeKey != "stonecutter") {
            yaml.appendLine("output: ${pending.output.type.name.lowercase()}")
            yaml.appendLine("lore: []")
            yaml.appendLine("ghost: ${pending.ghost}")
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
    val id: String,
    val permission: String
)
