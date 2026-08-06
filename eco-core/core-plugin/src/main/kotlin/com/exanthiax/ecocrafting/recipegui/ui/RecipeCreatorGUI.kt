package com.exanthiax.ecocrafting.recipegui.ui

import com.willfp.eco.core.gui.menu.MenuBuilder
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.util.formatEco
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.recipegui.integration.RecipeCreatorConfigWriter
import com.exanthiax.ecocrafting.recipegui.service.PendingRecipe
import com.exanthiax.ecocrafting.recipegui.service.RecipeGuiServices
import com.exanthiax.ecocrafting.recipegui.ui.wizard.openConfirmPreview
import com.exanthiax.ecocrafting.recipegui.ui.wizard.openIngredientSetup
import com.exanthiax.ecocrafting.recipegui.ui.wizard.openTypeSelect
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.Material
import org.bukkit.Registry
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player

// Entry point + shared session state for the in-game recipe-creation wizard. Each screen
// (type-select, ingredient-setup, output-setup, options, prompts) lives in its own file
// under recipegui/ui/wizard/ as an extension function on this class - Kotlin doesn't
// support splitting one class body across files, so extension functions are the
// idiomatic way to keep a single conceptual "class" under the file-size convention.
class RecipeCreatorGUI(
    internal val plugin: EcoCraftingPlugin,
    internal val guiServices: RecipeGuiServices
) {
    private val configWriter = RecipeCreatorConfigWriter(plugin)

    // Same column/row layout and item/lore config as RecipeGUI's workstation-marker
    // column (workstation-markers.<key>.active/inactive + lang.yml workstation-names),
    // just laid out for the wizard's fixed 6-row menus instead of driven by mask.pattern.
    internal val workstationIconPositions = listOf(
        Triple(1, 8, "crafting_table"), Triple(1, 9, "furnace"),
        Triple(2, 8, "blast_furnace"),  Triple(2, 9, "smoker"),
        Triple(3, 8, "campfire"),       Triple(3, 9, "brewing_stand"),
        Triple(4, 8, "smithing_table"), Triple(4, 9, "stonecutter"),
        Triple(5, 8, "grindstone"),     Triple(5, 9, "anvil"),
        Triple(6, 9, "villager")
    )

    internal val stationTypes = listOf(
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

    internal val villagerProfessionKeys: List<String> by lazy {
        Registry.VILLAGER_PROFESSION.mapNotNull { it.key.key }.sorted()
    }

    internal val pendingConfirm = mutableMapOf<UUID, PendingRecipe>()
    // AsyncChatEvent's handler (off-thread) and menu click handlers (main thread) both
    // write this concurrently, so a plain HashMap risks corruption/CME under load.
    val awaitingInput = ConcurrentHashMap<UUID, (String) -> Unit>()

    internal fun MenuBuilder.addWorkstationIcons(currentTypeKey: String) {
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

    internal fun MenuBuilder.fillBorder(rows: Int, usedCells: Set<Pair<Int, Int>>) {
        val filler = ItemStackBuilder(Material.BLACK_STAINED_GLASS_PANE).setDisplayName("").build()
        for (row in 1..rows) {
            for (col in 1..9) {
                if ((row to col) !in usedCells) setSlot(row, col, Slot.builder(filler).build())
            }
        }
    }

    fun startWizard(player: Player, typeKey: String?) {
        if (typeKey != null && typeKey in stationTypeKeys) {
            openIngredientSetup(player, typeKey)
        } else {
            openTypeSelect(player)
        }
    }

    fun existingRecipeIds(): List<String> = configWriter.existingRecipeIds()

    fun startEditWizard(player: Player, id: String) {
        val file = configWriter.findRecipeFile(id) ?: run {
            player.sendMessage("&cNo recipe found with ID '$id'.".formatEco())
            return
        }
        val yaml = YamlConfiguration.loadConfiguration(file)
        if (yaml.getString("type") == null) {
            player.sendMessage("&cRecipe '$id' has no type set.".formatEco())
            return
        }
        val seed = configWriter.parseEditSeed(yaml, id)
        openIngredientSetup(
            player,
            seed.typeKey,
            initialParts = seed.parts,
            initialShapeless = seed.shapeless,
            initialSymmetry = seed.symmetry,
            initialSupportCrafter = seed.supportCrafter,
            initialOutput = seed.output,
            editingId = seed.editingId,
            editingPermission = seed.editingPermission,
            initialState = seed.seedState
        )
    }

    fun openPreview(player: Player, pendingRecipe: PendingRecipe) {
        pendingConfirm[player.uniqueId] = pendingRecipe
        openConfirmPreview(player, pendingRecipe)
    }

    fun confirmSave(player: Player) {
        val pending = pendingConfirm.remove(player.uniqueId) ?: run {
            player.sendMessage("&cNo pending recipe to confirm.".formatEco())
            return
        }
        configWriter.saveRecipeYaml(pending)
        plugin.reload()
        player.sendMessage("&aRecipe '${pending.id}' saved and loaded.".formatEco())
    }

    fun cancelSave(player: Player) {
        pendingConfirm.remove(player.uniqueId)
        awaitingInput.remove(player.uniqueId)
        player.sendMessage("&cRecipe creation cancelled.".formatEco())
    }
}
