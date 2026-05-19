package ru.oftendev.recipebook.custom

import com.willfp.libreforge.EmptyProvidedHolder
import com.willfp.libreforge.SimpleProvidedHolder
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.TriggerData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.custom.libreforge.TriggerCustomCraft
import ru.oftendev.recipebook.custom.libreforge.TriggerGhostCraft
import ru.oftendev.recipebook.recipeBookPlugin

fun fireGhostEffects(player: Player, recipe: CustomRecipe, item: ItemStack, amount: Int) {
    val holder = recipe.ghostHolder ?: return
    val data = TriggerData(
        player = player,
        item = item,
        value = amount.toDouble()
    )
    TriggerGhostCraft.dispatch(
        player.toDispatcher(),
        data,
        listOf(SimpleProvidedHolder(holder))
    )
    TriggerCustomCraft.dispatch(
        player.toDispatcher(),
        data.copy(text = recipe.key.toString())
    )
}

fun fireStonecutterGhostEffects(
    player: Player,
    recipe: CustomRecipe.Stonecutter,
    output: StonecutterOutput,
    amount: Int
) {
    val holder = output.ghostHolder ?: return
    val data = TriggerData(
        player = player,
        item = output.item,
        value = amount.toDouble()
    )
    TriggerGhostCraft.dispatch(
        player.toDispatcher(),
        data,
        listOf(SimpleProvidedHolder(holder))
    )
    TriggerCustomCraft.dispatch(
        player.toDispatcher(),
        data.copy(text = recipe.key.toString())
    )
}

fun fireCustomCraftTrigger(player: Player, recipe: CustomRecipe, item: ItemStack, amount: Int) {
    val data = TriggerData(
        player = player,
        item = item,
        value = amount.toDouble(),
        text = recipe.key.toString()
    )
    TriggerCustomCraft.dispatch(player.toDispatcher(), data)
}

fun checkCraftingConditions(player: Player, recipe: CustomRecipe): Boolean {
    if (RecipeUnlockStore.isLocked(player, recipe)) {
        player.sendMessage(
            recipeBookPlugin.langYml.getFormattedString("messages.recipe-locked")
        )
        return false
    }
    val dispatcher = player.toDispatcher()
    val notMet = recipe.craftingConditions.getNotMetLines(dispatcher, EmptyProvidedHolder)
    if (notMet.isNotEmpty()) {
        notMet.forEach { player.sendMessage(it) }
        return false
    }
    return true
}
