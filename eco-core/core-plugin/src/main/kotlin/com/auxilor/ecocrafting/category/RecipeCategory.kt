package com.auxilor.ecocrafting.category

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import org.bukkit.entity.Player
import org.bukkit.inventory.CreativeCategory
import org.bukkit.inventory.ItemStack
import com.auxilor.ecocrafting.gui.CategoryCategoryGUI
import com.auxilor.ecocrafting.gui.ItemCategoryGUI
import com.auxilor.ecocrafting.recipe.RecipeResolver

data class CategoryPosition(val column: Int, val row: Int, val page: Int)

class RecipeCategory(val config: Config) {
    val id = config.getString("id")
    val type = config.getString("type").lowercase()
    val icon = config.getSubsectionOrNull("icon")?.let { CategoryIcon(it) }
    val items = config.getSubsections("items").mapNotNull { CategoryStack.from(this, it) }
    val categories = config.getStrings("categories")
    val guiPosition: CategoryPosition? = config.getSubsectionOrNull("position")?.let {
        CategoryPosition(it.getInt("column"), it.getInt("row"), it.getInt("page"))
    }
    val pullVanillaRecipes = runCatching { config.getBool("pull-vanilla-recipes") }.getOrDefault(false)
    val vanillaCreativeGroups: Set<CreativeCategory> = runCatching {
        config.getStrings("vanilla-creative-groups")
            .mapNotNull { runCatching { CreativeCategory.valueOf(it.uppercase()) }.getOrNull() }
            .toSet()
    }.getOrDefault(emptySet())

    val parsedCategories: List<RecipeCategory>
        get() = categories.mapNotNull { RecipeCategories.getById(it) }

    private val runtimeItems = mutableListOf<ItemStack>()
    private val vanillaItems = mutableListOf<ItemStack>()

    fun setVanillaItems(items: List<ItemStack>) {
        vanillaItems.clear()
        vanillaItems.addAll(items)
    }

    fun registerCustomRecipe(item: ItemStack) {
        runtimeItems += item
    }

    val gui = if (type == "items") {
        ItemCategoryGUI(config.getSubsection("gui"), this)
    } else {
        CategoryCategoryGUI(config.getSubsection("gui"))
    }

    fun getMemberItems(): List<ItemStack> {
        return if (type == "items") {
            emptyList()
        } else {
            parsedCategories.mapNotNull { it.icon?.getItemStack() }
        }
    }

    fun getMemberItemsRecipes(player: Player): List<ItemStack> {
        val configured = items.mapNotNull {
            if (canCraft(player, it.item.item)) {
                it.item.item
            } else if (it.displayNoPerm) {
                it.noPermItem ?: lockedFallback(it.item.item)
            } else {
                null
            }
        }
        return configured + vanillaItems + runtimeItems
    }

    private fun lockedFallback(item: ItemStack): ItemStack {
        return item.clone().apply { amount = 1 }
    }
}

fun canCraft(player: Player, itemStack: ItemStack): Boolean = RecipeResolver.canCraft(player, itemStack)

class CategoryStack private constructor(
    private val parent: RecipeCategory,
    val item: com.willfp.eco.core.items.TestableItem,
    val displayNoPerm: Boolean,
    private val configuredNoPermItem: ItemStack?
) {
    val noPermItem: ItemStack?
        get() = configuredNoPermItem?.clone()

    companion object {
        fun from(parent: RecipeCategory, config: Config): CategoryStack? {
            val itemString = config.getString("item")
            val item = runCatching { Items.lookup(itemString) }.getOrNull() ?: return null
            val noPermItem = config.getSubsectionOrNull("no-perm-item")?.let {
                ItemStackBuilder(Items.lookup(it.getString("item")))
                    .setDisplayName(it.getFormattedString("name"))
                    .addLoreLines(it.getFormattedStrings("lore"))
                    .build()
            }
            return CategoryStack(
                parent,
                item,
                config.getBool("display-no-perm"),
                noPermItem
            )
        }
    }
}

class CategoryIcon(private val config: Config) {
    fun getItemStack(): ItemStack? {
        val itemString = config.getString("item").replace("\\\"", "\"")
        return runCatching {
            ItemStackBuilder(Items.lookup(itemString))
                .addLoreLines(config.getFormattedStrings("lore"))
                .build()
        }.getOrNull()
    }
}
