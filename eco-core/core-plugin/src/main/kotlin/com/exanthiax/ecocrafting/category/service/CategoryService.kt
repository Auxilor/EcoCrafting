package com.exanthiax.ecocrafting.category.service

import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.category.integration.CategoryLoader
import com.exanthiax.ecocrafting.category.model.CategoryStack
import com.exanthiax.ecocrafting.category.model.RecipeCategory
import com.exanthiax.ecocrafting.recipe.service.RecipeResolverService
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

// Orchestration for category browsing: which items a player sees and can craft.
// The category itself (category/model/RecipeCategory) is data-only; this is the behaviour.
class CategoryService(
    private val plugin: EcoCraftingPlugin,
    private val categoryLoader: CategoryLoader,
    private val resolverService: RecipeResolverService
) {
    fun canCraft(player: Player, itemStack: ItemStack): Boolean = resolverService.canCraft(player, itemStack)

    fun getMemberItems(category: RecipeCategory): List<ItemStack> {
        return if (category.type == "items") {
            emptyList()
        } else {
            category.categories.mapNotNull { categoryLoader.getByID(it) }
                .mapNotNull { it.icon?.getItemStack() }
        }
    }

    fun getMemberItemsRecipes(category: RecipeCategory, player: Player): List<ItemStack> {
        val filterNoRecipe = !plugin.configYml.getBool("show-items-without-recipes")
        val configured = category.items.mapNotNull { filterConfiguredItem(player, it, filterNoRecipe) }
        val extra = category.extraItemStacks().filter { itemStack ->
            (!filterNoRecipe || resolverService.hasAnyRecipe(itemStack)) && canCraft(player, itemStack)
        }
        return configured + extra
    }

    private fun filterConfiguredItem(player: Player, stack: CategoryStack, filterNoRecipe: Boolean): ItemStack? {
        val itemStack = stack.item.item
        if (filterNoRecipe && !resolverService.hasAnyRecipe(itemStack)) return null
        return if (canCraft(player, itemStack)) {
            itemStack
        } else if (stack.displayNoPerm) {
            stack.noPermItem ?: lockedFallback(itemStack)
        } else {
            null
        }
    }

    private fun lockedFallback(item: ItemStack): ItemStack {
        return item.clone().apply { amount = 1 }
    }
}
