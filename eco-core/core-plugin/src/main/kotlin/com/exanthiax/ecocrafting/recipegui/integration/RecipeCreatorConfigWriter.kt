package com.exanthiax.ecocrafting.recipegui.integration

import com.willfp.eco.core.items.Items
import com.willfp.eco.core.recipe.parts.EmptyTestableItem
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.recipegui.service.PendingRecipe
import com.exanthiax.ecocrafting.recipegui.service.WizardState
import java.io.File
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack

// Uses eco's own lookup serialization so items (not just their base Material) round-trip
// through RecipeLoader.parseIngredient/parseOutputItem. A registered custom item is written
// as its key alone: toLookupString would also append every arg parser's output
// (`texture:...`, `name:"..."`), and those become exact-match predicates at load time that
// the item stops satisfying as soon as its texture or display name is edited.
internal fun itemLookupString(item: ItemStack?): String {
    if (item == null || item.isEmpty) return "air"
    val custom = Items.getCustomItem(item) ?: return Items.toLookupString(item)
    return if (item.amount > 1) "${custom.key} ${item.amount}" else custom.key.toString()
}

data class EditSeed(
    val typeKey: String,
    val parts: Map<Int, ItemStack>,
    val shapeless: Boolean,
    val symmetry: Boolean,
    val supportCrafter: Boolean,
    val output: ItemStack?,
    val editingId: String,
    val editingPermission: String?,
    val seedState: WizardState
)

// The config read/write half of the in-game recipe builder: finding/loading existing
// recipe files and serializing a finished wizard session back out to yaml. No GUI/menu
// code here - see recipegui/ui for the wizard screens themselves.
class RecipeCreatorConfigWriter(private val plugin: EcoCraftingPlugin) {

    fun existingRecipeIds(): List<String> {
        val dir = File(plugin.dataFolder, "recipes")
        if (!dir.exists()) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile && it.extension.equals("yml", ignoreCase = true) }
            .map { it.nameWithoutExtension }
            .toList()
    }

    fun findRecipeFile(id: String): File? {
        val dir = File(plugin.dataFolder, "recipes")
        if (!dir.exists()) return null
        return dir.walkTopDown()
            .firstOrNull { it.isFile && it.extension.equals("yml", ignoreCase = true) && it.nameWithoutExtension.equals(id, ignoreCase = true) }
    }

    // YAML double-quoted scalar: only backslash and the closing quote need escaping,
    // so free-form chat input (`:`, `#`, leading `*`, etc.) can't break the parse.
    private fun yamlQuote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    // Reconstructs a WizardState (and the raw fields the ingredient/output screens need)
    // from a previously-saved recipe yaml, so /ecocrafting edit can re-enter the wizard
    // pre-filled instead of starting from a blank recipe.
    fun parseEditSeed(yaml: YamlConfiguration, id: String): EditSeed {
        val typeKey = yaml.getString("type")!!.lowercase()

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
        // Stonecutters carry the flag per output, everything else at the top level.
        // Absent means true, matching RecipeLoader.parseMeta.
        val giveResultItemSection = if (typeKey == "stonecutter") yaml.getConfigurationSection("outputs.0") else yaml
        val giveResultItem = giveResultItemSection?.let {
            if (it.contains("give-result-item")) it.getBoolean("give-result-item") else null
        } ?: true

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
        seedState.giveResultItem = giveResultItem

        return EditSeed(
            typeKey = typeKey,
            parts = parts,
            shapeless = shapeless,
            symmetry = symmetry,
            supportCrafter = supportCrafter,
            output = output,
            editingId = id,
            editingPermission = permission,
            seedState = seedState
        )
    }

    fun saveRecipeYaml(pending: PendingRecipe) {
        val dir = File(plugin.dataFolder, "recipes")
        dir.mkdirs()
        val file = findRecipeFile(pending.id) ?: File(dir, "${pending.id}.yml")
        val yaml = StringBuilder()
        yaml.appendLine("type: ${pending.typeKey}")
        if (pending.category.isNotBlank()) yaml.appendLine("category: ${yamlQuote(pending.category)}")

        when (pending.typeKey) {
            "crafting_table", "crafter" -> {
                yaml.appendLine("shapeless: ${pending.shapeless}")
                yaml.appendLine("symmetry: ${pending.symmetry}")
                yaml.appendLine("support-crafter: ${pending.supportCrafter}")
                yaml.appendLine("recipe:")
                for (slot in 0..8) {
                    val item = pending.parts[slot]
                    val lookup = if (item == null || item.type.isAir) "\"\"" else itemLookupString(item)
                    yaml.appendLine("  - $lookup")
                }
            }
            "furnace", "blast_furnace", "smoker", "campfire" -> {
                yaml.appendLine("input: ${itemLookupString(pending.parts[0])}")
                pending.cookTime?.let { yaml.appendLine("cook-time: $it") }
                yaml.appendLine("experience: ${pending.experience}")
            }
            "smithing_table" -> {
                yaml.appendLine("template: ${itemLookupString(pending.parts[0])}")
                yaml.appendLine("base: ${itemLookupString(pending.parts[1])}")
                yaml.appendLine("addition: ${itemLookupString(pending.parts[2])}")
            }
            "stonecutter" -> {
                yaml.appendLine("input: ${itemLookupString(pending.parts[0])}")
                yaml.appendLine("outputs:")
                yaml.appendLine("  - item: ${itemLookupString(pending.output)}")
                yaml.appendLine("    lore: []")
                yaml.appendLine("    give-result-item: ${pending.giveResultItem}")
            }
            "brewing_stand" -> {
                yaml.appendLine("base: ${itemLookupString(pending.parts[0])}")
                yaml.appendLine("ingredient: ${itemLookupString(pending.parts[1])}")
                pending.brewTime?.let { yaml.appendLine("brew-time: $it") }
            }
            "grindstone" -> {
                yaml.appendLine("item1: ${itemLookupString(pending.parts[0])}")
                pending.parts[1]?.let { yaml.appendLine("item2: ${itemLookupString(it)}") }
            }
            "villager" -> {
                yaml.appendLine("input1: ${itemLookupString(pending.parts[0])}")
                pending.parts[1]?.let { yaml.appendLine("input2: ${itemLookupString(it)}") }
                if (pending.profession.isNotBlank()) yaml.appendLine("profession: ${pending.profession}")
                yaml.appendLine("min-level: ${pending.minLevel}")
                yaml.appendLine("chance: ${pending.chance}")
                yaml.appendLine("wandering-trader: ${pending.wanderingTrader}")
                yaml.appendLine("villager-xp: ${pending.villagerXp}")
            }
            "anvil" -> {
                yaml.appendLine("base: ${itemLookupString(pending.parts[0])}")
                pending.parts[1]?.let { yaml.appendLine("material: ${itemLookupString(it)}") }
                yaml.appendLine("repair-cost: ${pending.repairCost}")
            }
        }

        if (pending.typeKey != "stonecutter") {
            yaml.appendLine("output: ${itemLookupString(pending.output)}")
            yaml.appendLine("lore: []")
            yaml.appendLine("give-result-item: ${pending.giveResultItem}")
        }

        if (pending.permission.isNotBlank()) yaml.appendLine("permission: ${yamlQuote(pending.permission)}")
        yaml.appendLine("locked-by-default: ${pending.lockedByDefault}")
        yaml.appendLine("show-when-locked: ${pending.showWhenLocked}")
        yaml.appendLine("visibility-conditions: []")
        yaml.appendLine("crafting-conditions: []")
        yaml.appendLine("unlock-conditions: []")

        file.writeText(yaml.toString())
    }
}
