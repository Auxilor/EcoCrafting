package com.exanthiax.ecocrafting.quickcraft.service

import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.crafting.service.checkCraftingConditions
import com.exanthiax.ecocrafting.crafting.service.fireCraftEffects
import com.exanthiax.ecocrafting.recipe.model.RecipeIngredient
import com.exanthiax.ecocrafting.recipe.model.RecipeSource
import com.exanthiax.ecocrafting.recipe.model.ResolvedRecipe
import com.exanthiax.ecocrafting.recipe.service.RecipeResolverService
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class QuickCraftService(
    private val plugin: EcoCraftingPlugin,
    private val recipeService: RecipeService,
    private val resolverService: RecipeResolverService,
    private val unlockService: RecipeUnlockService,
    private val player: Player,
    private val recipe: ResolvedRecipe
) {
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
        if (!resolverService.canCraft(player, recipe)) {
            return CraftAttempt(false, CraftFailure.NoPermission)
        }

        val meta = if (recipe.source == RecipeSource.CUSTOM && recipe.key != null) {
            recipeService.getMeta(recipe.key)
        } else null
        val workstationRecipe = meta?.let { recipe.key?.let(WorkstationRecipes::getByKey) }

        // meta-gated recipes reuse the real crafting path's lock/condition/price check
        // (and its player-facing messages) so quick-craft can't bypass them.
        if (meta != null && workstationRecipe != null) {
            if (!checkCraftingConditions(plugin, unlockService, player, workstationRecipe, meta)) {
                val failure = if (!meta.price.canAfford(player)) CraftFailure.CannotAfford else CraftFailure.None
                return CraftAttempt(false, failure)
            }
        }

        val plan = buildConsumptionPlan() ?: return CraftAttempt(false, CraftFailure.MissingMaterials)
        val output = recipe.output.clone()
        val giveResultItem = meta?.giveResultItem ?: true
        if (giveResultItem && !canFitAfterPlan(plan, output)) {
            return CraftAttempt(false, CraftFailure.NoSpace)
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

        meta?.price?.pay(player, 1.0)

        if (giveResultItem) {
            val leftovers = player.inventory.addItem(output)
            if (leftovers.isNotEmpty()) {
                // This should not happen because canFit checked a simulated inventory.
                leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
            }
        }

        if (meta != null && workstationRecipe != null) {
            fireCraftEffects(player, workstationRecipe, meta, output, 1)
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
    val failure: CraftFailure = CraftFailure.None
)

sealed class CraftFailure {
    object None : CraftFailure()
    object NoPermission : CraftFailure()
    object MissingMaterials : CraftFailure()
    object NoSpace : CraftFailure()
    object CannotAfford : CraftFailure()
    data class Custom(val text: String) : CraftFailure()
}
