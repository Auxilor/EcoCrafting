package com.exanthiax.ecocrafting.recipegui.ui.utils

import com.exanthiax.ecocrafting.recipe.model.ResolvedRecipe
import com.exanthiax.ecocrafting.recipegui.service.RecipeGuiServices
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import wiki.ecohub.config.Config
import wiki.ecohub.game.item.builder.ItemStackBuilder
import wiki.ecohub.game.sound.PlayableSound
import wiki.ecohub.modules.menus.menu.Menu

class RecipeGUIContext(
    val services: RecipeGuiServices,
    val config: Config,
    val stack: ItemStack,
    val effectiveAlternatives: List<ResolvedRecipe>,
    val altIndex: Int,
    val parent: Menu?
) {
    val globalFlags: Array<ItemFlag> = services.plugin.configYml.getStrings("item-flags")
        .mapNotNull { runCatching { ItemFlag.valueOf(it.uppercase()) }.getOrNull() }
        .toTypedArray()

    fun sound(key: String) =
        services.plugin.configYml.getSubsectionOrNull("sounds.$key")
            ?.let { PlayableSound.create(it) }

    fun ItemStackBuilder.withGlobalFlags(): ItemStackBuilder =
        if (globalFlags.isEmpty()) this else addItemFlag(*globalFlags)
}
