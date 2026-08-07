package com.exanthiax.ecocrafting.recipe.integration

import com.exanthiax.ecocrafting.category.model.RecipeCategory
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Keyed
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class VanillaRecipeScanner {

    // Vanilla recipes never change at runtime - collect once and reuse across reloads.
    private val vanillaOutputs: List<ItemStack> by lazy { collectVanillaOutputs() }

    fun populate(categories: List<RecipeCategory>) {
        val wantsVanilla = categories.filter { it.pullVanillaRecipes }
        if (wantsVanilla.isEmpty()) return

        val vanilla = vanillaOutputs

        for (category in wantsVanilla) {
            val configuredMaterials = category.items.map { it.item.item.type }.toSet()
            category.setVanillaItems(
                vanilla.filter { it.type !in configuredMaterials }
            )
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
