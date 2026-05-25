package com.auxilor.ecocrafting.recipe

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Keyed
import org.bukkit.Material
import org.bukkit.inventory.CreativeCategory
import org.bukkit.inventory.ItemStack
import com.auxilor.ecocrafting.category.RecipeCategory

object VanillaRecipeScanner {

    // Vanilla recipes never change at runtime â€” collect once and reuse across reloads.
    private val vanillaOutputs: List<ItemStack> by lazy { collectVanillaOutputs() }

    fun populate(categories: List<RecipeCategory>) {
        val wantsVanilla = categories.filter {
            it.pullVanillaRecipes || it.vanillaCreativeGroups.isNotEmpty()
        }
        if (wantsVanilla.isEmpty()) return

        val vanilla = vanillaOutputs

        for (category in wantsVanilla) {
            val configuredMaterials = category.items.map { it.item.item.type }.toSet()
            val items = buildCategoryItems(
                configuredMaterials = configuredMaterials,
                pullAll = category.pullVanillaRecipes,
                creativeGroups = category.vanillaCreativeGroups,
                candidates = vanilla
            )
            category.setVanillaItems(items)
        }
    }

    private fun collectVanillaOutputs(): List<ItemStack> {
        val seen = mutableSetOf<Material>()
        val results = mutableListOf<ItemStack>()

        val iter = Bukkit.recipeIterator()
        while (iter.hasNext()) {
            val recipe = iter.next()
            val keyed = recipe as? Keyed ?: continue
            if (keyed.key.namespace != "minecraft") continue
            val output = recipe.result
            if (output.type == Material.AIR) continue
            if (seen.add(output.type)) results.add(ItemStack(output.type))
        }

        // recipeIterator may omit furnace/stonecutter/smoker in some Paper builds;
        // supplement by checking getRecipesFor every item material
        for (material in Material.values()) {
            if (material.isAir || !material.isItem || material in seen) continue
            val hasVanilla = Bukkit.getRecipesFor(ItemStack(material)).any { recipe ->
                (recipe as? Keyed)?.key?.namespace == "minecraft"
            }
            if (hasVanilla && seen.add(material)) results.add(ItemStack(material))
        }

        return results
    }

    internal fun buildCategoryItems(
        configuredMaterials: Set<Material>,
        pullAll: Boolean,
        creativeGroups: Set<CreativeCategory>,
        candidates: List<ItemStack>,
        nameFor: (ItemStack) -> String = ::displayNameFor
    ): List<ItemStack> {
        val seen = mutableSetOf<Material>()
        return candidates
            .filter { item ->
                val includeByBool = pullAll
                val includeByGroup = creativeGroups.isNotEmpty() &&
                    item.type.creativeCategory?.let { it in creativeGroups } == true
                (includeByBool || includeByGroup) &&
                    item.type !in configuredMaterials &&
                    seen.add(item.type)
            }
            .sortedBy { nameFor(it) }
    }

    internal fun displayNameFor(item: ItemStack): String {
        return item.itemMeta
            ?.takeIf { it.hasDisplayName() }
            ?.displayName()
            ?.let { PlainTextComponentSerializer.plainText().serialize(it) }
            ?: formatMaterialName(item.type)
    }

    internal fun formatMaterialName(material: Material): String = formatName(material.name)

    internal fun formatName(name: String): String =
        name.lowercase().split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
