package ru.oftendev.recipebook.category

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.gui.CategoryCategoryGUI
import ru.oftendev.recipebook.gui.ItemCategoryGUI
import ru.oftendev.recipebook.recipe.RecipeResolver

class RecipeCategory(val config: Config) {
    val id = config.getString("id")
    val type = config.getString("type").lowercase()
    val icon = config.getSubsectionOrNull("icon")?.let { CategoryIcon(it) }
    val items = config.getSubsections("items").mapNotNull { CategoryStack.from(this, it) }
    val categories = config.getStrings("categories")

    val parsedCategories: List<RecipeCategory>
        get() = categories.mapNotNull { RecipeCategories.getById(it) }

    val gui = if (type == "items") {
        ItemCategoryGUI(config.getSubsection("gui"), this)
    } else {
        CategoryCategoryGUI(config.getSubsection("gui"), this)
    }

    fun getMemberItems(): List<ItemStack> {
        return if (type == "items") {
            emptyList()
        } else {
            parsedCategories.mapNotNull { it.icon?.getItemStack() }
        }
    }

    fun getMemberItemsRecipes(player: Player): List<ItemStack> {
        return items.mapNotNull {
            if (canCraft(player, it.item.item)) {
                it.item.item
            } else if (it.displayNoPerm) {
                it.noPermItem ?: lockedFallback(it.item.item)
            } else {
                null
            }
        }
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
