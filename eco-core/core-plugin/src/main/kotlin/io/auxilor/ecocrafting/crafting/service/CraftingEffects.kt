package io.auxilor.ecocrafting.crafting.service

import com.willfp.eco.core.price.ConfiguredPrice
import com.willfp.eco.core.recipe.workstation.WorkstationRecipe
import com.willfp.libreforge.EmptyProvidedHolder
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.TriggerData
import io.auxilor.ecocrafting.EcoCraftingPlugin
import io.auxilor.ecocrafting.libreforge.TriggerCraft
import io.auxilor.ecocrafting.libreforge.TriggerGhostCraft
import io.auxilor.ecocrafting.recipe.model.EcoCraftingMeta
import io.auxilor.ecocrafting.unlock.service.RecipeUnlockService
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
    if (!meta.giveResultItem) TriggerGhostCraft.dispatch(player.toDispatcher(), data)
    TriggerCraft.dispatch(player.toDispatcher(), data)
}

fun checkCraftingConditions(
    plugin: EcoCraftingPlugin,
    unlockService: RecipeUnlockService,
    player: Player,
    recipe: WorkstationRecipe,
    meta: EcoCraftingMeta
): Boolean {
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
    val notMet = meta.craftingConditions.getNotMetLines(dispatcher, EmptyProvidedHolder)
    if (notMet.isNotEmpty()) {
        notMet.forEach { player.sendMessage(it) }
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
