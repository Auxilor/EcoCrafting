package io.auxilor.ecocrafting.recipegui.ui.utils

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.sound.PlayableSound
import io.auxilor.ecocrafting.recipe.model.ResolvedRecipe
import io.auxilor.ecocrafting.recipegui.service.RecipeGuiServices
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack

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
