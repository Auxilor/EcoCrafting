package io.auxilor.ecocrafting.category.model

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.TestableItem
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.recipe.parts.GroupedTestableItems
import com.willfp.eco.core.registry.KRegistrable
import io.auxilor.ecocrafting.api.category.Category
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Tag
import org.bukkit.inventory.ItemStack

data class CategoryPosition(val column: Int, val row: Int, val page: Int)

class RecipeCategory(
    override val id: String,
    val config: Config
) : KRegistrable, Category {
    val type = config.getString("type").lowercase()
    val icon = config.getSubsectionOrNull("icon")?.let { CategoryIcon(it) }
    val items = config.getSubsections("items").flatMap { CategoryStack.fromAll(this, it) }
    val categories = config.getStrings("categories")
    val guiPosition: CategoryPosition? = config.getSubsectionOrNull("position")?.let {
        CategoryPosition(it.getInt("column"), it.getInt("row"), it.getInt("page"))
    }
    val pullVanillaRecipes = config.getBool("pull-vanilla-recipes")

    private val runtimeItems = mutableListOf<ItemStack>()
    private val vanillaItems = mutableListOf<ItemStack>()

    fun setVanillaItems(items: List<ItemStack>) {
        vanillaItems.clear()
        vanillaItems.addAll(items)
    }

    fun registerCustomRecipe(item: ItemStack) {
        runtimeItems += item
    }

    fun allItemStacks(): List<ItemStack> {
        return items.map { it.item.item } + vanillaItems + runtimeItems
    }

    fun extraItemStacks(): List<ItemStack> = vanillaItems + runtimeItems

    override fun getID(): String {
        return id
    }
}

class CategoryStack private constructor(
    private val parent: RecipeCategory,
    val item: TestableItem,
    val displayNoPerm: Boolean,
    private val configuredNoPermItem: ItemStack?
) {
    val noPermItem: ItemStack?
        get() = configuredNoPermItem?.clone()

    companion object {
        fun fromAll(parent: RecipeCategory, config: Config): List<CategoryStack> {
            val itemString = config.getString("item")
            val displayNoPerm = config.getBool("display-no-perm")
            val noPermItem = config.getSubsectionOrNull("no-perm-item")?.let {
                ItemStackBuilder(Items.lookup(it.getString("item")))
                    .setDisplayName(it.getFormattedString("name"))
                    .addLoreLines(it.getFormattedStrings("lore"))
                    .build()
            }
            val children = expandItemString(itemString)
            return children.map { child -> CategoryStack(parent, child, displayNoPerm, noPermItem) }
        }

        private fun expandItemString(itemString: String): List<TestableItem> {
            if (!itemString.startsWith("#")) {
                return listOf(Items.lookup(itemString))
            }

            val tagKey = itemString.removePrefix("#")
            val namespacedKey = if (":" in tagKey) {
                val (namespace, key) = tagKey.split(":", limit = 2)
                NamespacedKey(namespace, key)
            } else {
                NamespacedKey.minecraft(tagKey)
            }

            val bukkitTag = Bukkit.getTag(Tag.REGISTRY_ITEMS, namespacedKey, Material::class.java)
            if (bukkitTag != null) {
                return bukkitTag.values.map { Items.lookup(it.name.lowercase()) }
            }

            // Eco custom tag fallback - enumerate via matches()
            val lookup = Items.lookup(itemString)
            if (lookup is GroupedTestableItems) return lookup.children.toList()
            return Material.entries
                .filter { !it.isAir && it.isItem && lookup.matches(ItemStack(it)) }
                .map { Items.lookup(it.name.lowercase()) }
        }
    }
}

class CategoryIcon(private val config: Config) {
    fun getItemStack(): ItemStack? {
        val itemString = config.getString("item").replace("\\\"", "\"")
        return ItemStackBuilder(Items.lookup(itemString))
            .addLoreLines(config.getFormattedStrings("lore"))
            .build()
    }
}
