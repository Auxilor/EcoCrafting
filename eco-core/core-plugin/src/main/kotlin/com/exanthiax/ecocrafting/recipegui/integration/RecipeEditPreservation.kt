package com.exanthiax.ecocrafting.recipegui.integration

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

private val BUILDER_MANAGED_KEYS = setOf(
    "type", "category", "shapeless", "symmetry", "support-crafter", "recipe",
    "input", "cook-time", "experience", "template", "base", "addition", "outputs",
    "ingredient", "brew-time", "item1", "item2", "input1", "input2", "profession",
    "min-level", "chance", "wandering-trader", "villager-xp", "material", "repair-cost",
    "output", "give-result-item", "permission", "locked-by-default", "show-when-locked"
)

private val BUILDER_DEFAULT_KEYS = listOf("lore", "visibility-conditions", "crafting-conditions", "unlock-conditions")

private val BUILDER_OUTPUT_KEYS = setOf("item", "give-result-item")

private fun ConfigurationSection.toPlainMap(): Map<String, Any?> = getKeys(false).associateWith { key ->
    get(key).let { value -> if (value is ConfigurationSection) value.toPlainMap() else value }
}

/**
 * Every top-level key of an existing recipe file that the builder does not write itself,
 * with its comments, so editing a recipe never drops hand-written config such as `price`.
 */
internal fun carriedOverYaml(existing: YamlConfiguration?): String {
    if (existing == null) return ""
    val carried = YamlConfiguration()
    existing.getKeys(false).filter { it !in BUILDER_MANAGED_KEYS }.forEach { key ->
        carried.set(key, existing.get(key).let { if (it is ConfigurationSection) it.toPlainMap() else it })
        carried.setComments(key, existing.getComments(key))
    }
    return carried.saveToString()
}

internal fun builderDefaultLines(existing: YamlConfiguration?, includeLore: Boolean = true): List<String> =
    BUILDER_DEFAULT_KEYS
        .filter { includeLore || it != "lore" }
        .filter { existing?.contains(it) != true }
        .map { "$it: []" }

internal fun stonecutterOutputsYaml(existing: YamlConfiguration?, item: String, giveResultItem: Boolean): String {
    val existingOutputs = existing?.getMapList("outputs").orEmpty()
    val existingFirst = existingOutputs.firstOrNull().orEmpty()
    val output = linkedMapOf<String, Any?>("item" to item)
    if (!existingFirst.containsKey("lore")) output["lore"] = emptyList<String>()
    output["give-result-item"] = giveResultItem
    existingFirst.forEach { (key, value) -> if (key.toString() !in BUILDER_OUTPUT_KEYS) output[key.toString()] = value }
    return YamlConfiguration().apply { set("outputs", listOf(output) + existingOutputs.drop(1)) }.saveToString()
}
