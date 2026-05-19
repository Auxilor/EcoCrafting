package ru.oftendev.recipebook.gui

import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.MaskItems
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.builder.ItemStackBuilder
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.custom.CustomRecipes
import ru.oftendev.recipebook.custom.RecipeUnlockStore
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
        Triple("cartography_table", Material.CARTOGRAPHY_TABLE, "&aCartography Table"),
        Triple("grindstone",        Material.GRINDSTONE,        "&aGrindstone"),
        Triple("anvil",             Material.ANVIL,             "&aAnvil"),
        Triple("villager",          Material.EMERALD,           "&aVillager Trade")
    )

    fun openTypeSelect(player: Player) {
        val menu = Menu.builder(2).setTitle("&8New Recipe — Choose Type")

        stationTypes.forEachIndexed { idx, (typeKey, mat, label) ->
            val row = (idx / 9) + 1
            val col = (idx % 9) + 1
            menu.setSlot(row, col, Slot.builder(
                ItemStackBuilder(mat).setDisplayName(label).build()
            ).onLeftClick { _, _ ->
                openIngredientSetup(player, typeKey)
            }.build())
        }

        menu.setMask(FillerMask(
            MaskItems.fromItemNames(listOf("black_stained_glass_pane")),
            "000000000", "000000000"
        ))
        menu.build().open(player)
    }

    private fun openIngredientSetup(player: Player, typeKey: String) {
        val slotLayout = ingredientSlotLayout(typeKey)
        val menu = Menu.builder(4).setTitle("&8New Recipe — Ingredients")
        val collectedParts = mutableMapOf<Int, ItemStack>()

        slotLayout.forEachIndexed { idx, (row, col) ->
            menu.setSlot(row, col, Slot.builder(
                ItemStackBuilder(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .setDisplayName("&7Slot ${idx + 1} — place ingredient").build()
            ).onLeftClick { event, _ ->
                val cursor = event.cursor ?: return@onLeftClick
                if (cursor.type.isAir) return@onLeftClick
                collectedParts[idx] = cursor.clone().apply { amount = 1 }
                event.inventory.setItem(event.rawSlot, ItemStackBuilder(cursor.clone()).build())
            }.onRightClick { event, _ ->
                collectedParts.remove(idx)
                event.inventory.setItem(
                    event.rawSlot,
                    ItemStackBuilder(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                        .setDisplayName("&7Slot ${idx + 1} — place ingredient").build()
                )
            }.build())
        }

        if (typeKey == "crafting_table") {
            var shapeless = false
            menu.setSlot(4, 9, Slot.builder(
                ItemStackBuilder(Material.PAPER).setDisplayName("&eShaped (click to toggle)").build()
            ).onLeftClick { event, _ ->
                shapeless = !shapeless
                val label = if (shapeless) "&eShapeless" else "&eShaped"
                event.inventory.setItem(event.rawSlot, ItemStackBuilder(Material.PAPER).setDisplayName(label).build())
            }.build())
        }

        menu.setSlot(4, 5, Slot.builder(
            ItemStackBuilder(Material.LIME_DYE).setDisplayName("&aNext →").build()
        ).onLeftClick { _, _ ->
            player.closeInventory()
            openOutputSetup(player, typeKey, collectedParts)
        }.build())

        menu.setMask(FillerMask(
            MaskItems.fromItemNames(listOf("black_stained_glass_pane")),
            "111111111", "111111111", "111111111", "111111111"
        ))
        menu.build().open(player)
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
        "cartography_table", "grindstone", "villager" -> listOf(2 to 2, 2 to 4)
        "anvil" -> listOf(2 to 2, 2 to 4)
        else -> emptyList()
    }

    fun openOutputSetup(player: Player, typeKey: String, parts: Map<Int, ItemStack>) {
        var ghost = false
        val menu = Menu.builder(3).setTitle("&8New Recipe — Output")

        menu.setSlot(2, 5, Slot.builder(
            ItemStackBuilder(Material.LIGHT_GRAY_STAINED_GLASS_PANE).setDisplayName("&7Place output item").build()
        ).onLeftClick { event, _ ->
            val cursor = event.cursor ?: return@onLeftClick
            if (cursor.type.isAir) return@onLeftClick
            event.inventory.setItem(event.rawSlot, cursor.clone())
        }.build())

        menu.setSlot(2, 7, Slot.builder(
            ItemStackBuilder(Material.GRAY_DYE).setDisplayName("&7Ghost: OFF").build()
        ).onLeftClick { event, _ ->
            ghost = !ghost
            val label = if (ghost) "&aGhost: ON" else "&7Ghost: OFF"
            val mat   = if (ghost) Material.LIME_DYE else Material.GRAY_DYE
            event.inventory.setItem(event.rawSlot, ItemStackBuilder(mat).setDisplayName(label).build())
        }.build())

        menu.setSlot(3, 5, Slot.builder(
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

        menu.setMask(FillerMask(
            MaskItems.fromItemNames(listOf("black_stained_glass_pane")),
            "111111111", "111111111", "111111111"
        ))
        menu.build().open(player)
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
            if (CustomRecipes.getByKey(NamespacedKey("recipebook", cleanId)) != null) {
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
        val sb = StringBuilder()
        sb.appendLine("type: ${pending.typeKey}")

        when (pending.typeKey) {
            "crafting_table", "crafter" -> {
                sb.appendLine("shapeless: false")
                sb.appendLine("recipe:")
                for (i in 0..8) {
                    val item = pending.parts[i]
                    val lookup = if (item == null || item.type.isAir) "\"\"" else item.type.name.lowercase()
                    sb.appendLine("  - $lookup")
                }
            }
            "furnace", "blast_furnace", "smoker", "campfire" -> {
                val input = pending.parts[0]?.type?.name?.lowercase() ?: "air"
                sb.appendLine("input: $input")
                sb.appendLine("cook-time: 200")
                sb.appendLine("experience: 0.0")
            }
            "smithing_table" -> {
                sb.appendLine("template: ${pending.parts[0]?.type?.name?.lowercase() ?: "air"}")
                sb.appendLine("base: ${pending.parts[1]?.type?.name?.lowercase() ?: "air"}")
                sb.appendLine("addition: ${pending.parts[2]?.type?.name?.lowercase() ?: "air"}")
            }
            "stonecutter" -> {
                sb.appendLine("input: ${pending.parts[0]?.type?.name?.lowercase() ?: "air"}")
                sb.appendLine("outputs:")
                sb.appendLine("  - item: ${pending.output.type.name.lowercase()}")
                sb.appendLine("    lore: []")
                sb.appendLine("    ghost: ${pending.ghost}")
                if (pending.ghost) { sb.appendLine("    effects: []"); sb.appendLine("    conditions: []") }
            }
            "brewing_stand" -> {
                sb.appendLine("base: ${pending.parts[0]?.type?.name?.lowercase() ?: "air"}")
                sb.appendLine("ingredient: ${pending.parts[1]?.type?.name?.lowercase() ?: "air"}")
            }
            "cartography_table", "grindstone", "villager" -> {
                sb.appendLine("input1: ${pending.parts[0]?.type?.name?.lowercase() ?: "air"}")
                pending.parts[1]?.let { sb.appendLine("input2: ${it.type.name.lowercase()}") }
            }
            "anvil" -> {
                sb.appendLine("base: ${pending.parts[0]?.type?.name?.lowercase() ?: "air"}")
                pending.parts[1]?.let { sb.appendLine("material: ${it.type.name.lowercase()}") }
                sb.appendLine("repair-cost: 1")
            }
        }

        if (pending.typeKey != "stonecutter") {
            sb.appendLine("output: ${pending.output.type.name.lowercase()}")
            sb.appendLine("lore: []")
            sb.appendLine("ghost: ${pending.ghost}")
            if (pending.ghost) {
                sb.appendLine("effects: []")
                sb.appendLine("conditions: []")
            }
        }

        if (pending.permission.isNotBlank()) sb.appendLine("permission: \"${pending.permission}\"")
        sb.appendLine("locked-by-default: false")
        sb.appendLine("visibility-conditions: []")
        sb.appendLine("crafting-conditions: []")
        sb.appendLine("unlock-conditions: []")

        file.writeText(sb.toString())
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
