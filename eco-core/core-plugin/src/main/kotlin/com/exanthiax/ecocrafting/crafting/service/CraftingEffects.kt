package com.exanthiax.ecocrafting.crafting.service

import com.willfp.eco.core.price.ConfiguredPrice
import com.willfp.eco.core.recipe.workstation.WorkstationRecipe
import com.willfp.libreforge.EmptyProvidedHolder
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.TriggerData
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.libreforge.TriggerCraft
import com.exanthiax.ecocrafting.recipe.model.EcoCraftingMeta
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

fun fireCraftEffects(
    player: Player,
    recipe: WorkstationRecipe,
    meta: EcoCraftingMeta,
    item: ItemStack,
    amount: Int,
    block: Block? = null
) {
    val data = TriggerData(player = player, item = item, value = amount.toDouble(), text = recipe.key.toString(), block = block)
    meta.effectsChain?.trigger(player.toDispatcher(), data)
    TriggerCraft.dispatch(player.toDispatcher(), data)
}

// A recipe's explicit `permission` wins outright; otherwise the implicit per-recipe node
// (or the wildcard) grants access.
fun hasRecipePermission(player: Player, recipe: WorkstationRecipe): Boolean {
    val explicitPermission = recipe.permission
    return if (explicitPermission != null) {
        player.hasPermission(explicitPermission)
    } else {
        player.hasPermission("ecocrafting.recipe.${recipe.key.key}") || player.hasPermission("ecocrafting.recipe.*")
    }
}

fun checkCraftingConditions(
    plugin: EcoCraftingPlugin,
    unlockService: RecipeUnlockService,
    player: Player,
    recipe: WorkstationRecipe,
    meta: EcoCraftingMeta
): Boolean {
    if (!hasRecipePermission(player, recipe)) {
        player.sendMessage(plugin.langYml.getFormattedString("messages.failed-reason.no-permission"))
        return false
    }
    if (unlockService.isLocked(player, recipe.key, meta)) {
        player.sendMessage(plugin.langYml.getFormattedString("messages.recipe-locked"))
        return false
    }
    if (!meta.price.canAfford(player)) {
        player.sendMessage(
            plugin.langYml.getFormattedString("messages.failed-reason.cannot-afford")
                .replace("%price%", meta.price.getDisplay(player))
        )
        return false
    }
    val dispatcher = player.toDispatcher()
    if (!meta.craftingConditions.areMet(dispatcher, EmptyProvidedHolder)) {
        val notMet = meta.craftingConditions.getNotMetLines(dispatcher, EmptyProvidedHolder)
        if (notMet.isNotEmpty()) {
            notMet.forEach { player.sendMessage(it) }
        } else {
            player.sendMessage(plugin.langYml.getFormattedString("messages.craft-conditions-not-met"))
        }
        return false
    }
    return true
}

fun priceAffordableAmount(player: Player, price: ConfiguredPrice, desired: Int): Int {
    if (desired <= 0) return 0
    if (price.canAfford(player, desired.toDouble())) return desired
    var low = 0
    var high = desired
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (price.canAfford(player, mid.toDouble())) low = mid else high = mid - 1
    }
    return low
}
