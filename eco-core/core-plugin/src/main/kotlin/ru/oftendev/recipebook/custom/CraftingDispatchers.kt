package ru.oftendev.recipebook.custom

import com.willfp.eco.core.recipe.workstation.WorkstationRecipe
import com.willfp.libreforge.EmptyProvidedHolder
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.TriggerData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.custom.libreforge.TriggerCustomCraft
import ru.oftendev.recipebook.custom.libreforge.TriggerGhostCraft
import ru.oftendev.recipebook.recipeBookPlugin

fun fireCraftEffects(player: Player, recipe: WorkstationRecipe, meta: RecipeBookMeta, item: ItemStack, amount: Int) {
    val data = TriggerData(player = player, item = item, value = amount.toDouble(), text = recipe.key.toString())
    meta.effectsChain?.trigger(player.toDispatcher(), data)
    if (!meta.giveResultItem) TriggerGhostCraft.dispatch(player.toDispatcher(), data)
    TriggerCustomCraft.dispatch(player.toDispatcher(), data)
}

fun checkCraftingConditions(player: Player, recipe: WorkstationRecipe, meta: RecipeBookMeta): Boolean {
    if (RecipeUnlockStore.isLocked(player, recipe.key, meta)) {
        player.sendMessage(recipeBookPlugin.langYml.getFormattedString("messages.recipe-locked"))
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
