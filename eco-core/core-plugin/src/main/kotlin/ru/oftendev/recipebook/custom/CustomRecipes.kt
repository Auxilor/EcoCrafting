package ru.oftendev.recipebook.custom

import com.willfp.eco.core.items.HashedItem
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack

object CustomRecipes {
    private val byKey = mutableMapOf<NamespacedKey, CustomRecipe>()
    private val byOutput = mutableMapOf<HashedItem, CustomRecipe>()
    private val registeredBukkitKeys = mutableSetOf<NamespacedKey>()

    fun register(recipe: CustomRecipe) {
        byKey[recipe.key] = recipe
        when (recipe) {
            is CustomRecipe.Stonecutter -> recipe.outputs.forEach { out ->
                byOutput[HashedItem.of(out.item.clone().apply { amount = 1 })] = recipe
            }
            else -> byOutput[HashedItem.of(recipe.output.clone().apply { amount = 1 })] = recipe
        }
    }

    fun trackBukkitKey(key: NamespacedKey) {
        registeredBukkitKeys.add(key)
    }

    fun clear() {
        registeredBukkitKeys.forEach { Bukkit.removeRecipe(it) }
        registeredBukkitKeys.clear()
        byKey.clear()
        byOutput.clear()
    }

    fun getByKey(key: NamespacedKey): CustomRecipe? = byKey[key]

    fun getByOutput(stack: ItemStack): CustomRecipe? =
        byOutput[HashedItem.of(stack.clone().apply { amount = 1 })]

    fun all(): Collection<CustomRecipe> = byKey.values
}
