package com.auxilor.ecocrafting.craft

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import com.auxilor.ecocrafting.recipe.RecipeIngredient
import com.auxilor.ecocrafting.recipe.ResolvedRecipe

class QuickCraftService(private val player: Player, private val recipe: ResolvedRecipe) {
    fun getMaterialCounts(): List<MaterialCount> {
        return recipe.ingredients
            .filter { !it.empty && !it.displayItem.type.isAir }
            .groupBy { it.displayItem.displayKey() }
            .map { (_, ingredients) ->
                val sample = ingredients.first().displayItem.clone().apply { amount = 1 }
                val needed = ingredients.sumOf { it.displayItem.amount.coerceAtLeast(1) }
                val has = countMatching(ingredients.first())
                MaterialCount(sample, has, needed)
            }
    }

    fun getMissingMaterials(): List<Pair<ItemStack, Int>> {
        return getMaterialCounts()
            .filter { it.has < it.needs }
            .map { it.item.clone().apply { amount = 1 } to (it.needs - it.has) }
    }

    fun craft(): CraftAttempt {
        if (recipe.permission != null && !player.hasPermission(recipe.permission)) {
            return CraftAttempt(false, "No permission")
        }

        val plan = buildConsumptionPlan() ?: return CraftAttempt(false, "Missing materials")
        val output = recipe.output.clone()
        if (!canFitAfterPlan(plan, output)) {
            return CraftAttempt(false, "No inventory space")
        }

        for ((slot, amount) in plan) {
            val current = player.inventory.getItem(slot) ?: continue
            val remaining = current.amount - amount
            if (remaining <= 0) {
                player.inventory.setItem(slot, null)
            } else {
                current.amount = remaining
                player.inventory.setItem(slot, current)
            }
        }

        val leftovers = player.inventory.addItem(output)
        if (leftovers.isNotEmpty()) {
            // This should not happen because canFit checked a simulated inventory.
            leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
        }

        return CraftAttempt(true)
    }

    private fun countMatching(ingredient: RecipeIngredient): Int {
        return player.inventory.storageContents
            .filterNotNull()
            .filter { ingredient.matches(it) }
            .sumOf { it.amount }
    }

    private fun buildConsumptionPlan(): Map<Int, Int>? {
        val remaining = recipe.ingredients
            .filter { !it.empty && !it.displayItem.type.isAir }
            .flatMap { ingredient ->
                List(ingredient.displayItem.amount.coerceAtLeast(1)) { ingredient }
            }
            .toMutableList()

        val consumption = mutableMapOf<Int, Int>()
        val contents = player.inventory.storageContents

        for (slot in contents.indices) {
            val stack = contents[slot] ?: continue
            var available = stack.amount
            val iterator = remaining.listIterator()
            while (iterator.hasNext() && available > 0) {
                val ingredient = iterator.next()
                if (ingredient.matches(stack)) {
                    consumption[slot] = (consumption[slot] ?: 0) + 1
                    available--
                    iterator.remove()
                }
            }
            if (remaining.isEmpty()) break
        }

        return if (remaining.isEmpty()) consumption else null
    }

    private fun canFitAfterPlan(plan: Map<Int, Int>, output: ItemStack): Boolean {
        val clone = player.inventory.storageContents.map { it?.clone() }.toTypedArray()

        for ((slot, amount) in plan) {
            val stack = clone.getOrNull(slot) ?: continue
            val remaining = stack.amount - amount
            clone[slot] = if (remaining <= 0) null else stack.apply { this.amount = remaining }
        }

        val fake = output.clone()
        var remaining = fake.amount

        for (slot in clone.indices) {
            val stack = clone[slot]
            if (stack == null || stack.type.isAir) {
                remaining = 0
                break
            }
            if (stack.isSimilar(fake) && stack.amount < stack.maxStackSize) {
                val canAdd = stack.maxStackSize - stack.amount
                val add = minOf(canAdd, remaining)
                stack.amount += add
                remaining -= add
                if (remaining <= 0) break
            }
        }

        return remaining <= 0
    }

    private fun ItemStack.displayKey(): String {
        return if (hasItemMeta()) {
            "${type.name}:${itemMeta.persistentDataContainer.hashCode()}:${itemMeta.displayName()}"
        } else {
            type.name
        }
    }
}

data class MaterialCount(
    val item: ItemStack,
    val has: Int,
    val needs: Int
)

data class CraftAttempt(
    val success: Boolean,
    val reason: String = ""
)
