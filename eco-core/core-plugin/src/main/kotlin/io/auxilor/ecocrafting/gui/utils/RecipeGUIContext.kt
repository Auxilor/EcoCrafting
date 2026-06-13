package io.auxilor.ecocrafting.gui.utils

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.sound.PlayableSound
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import io.auxilor.ecocrafting.recipe.ResolvedRecipe
import io.auxilor.ecocrafting.plugin

class RecipeGUIContext(
    val config: Config,
    val stack: ItemStack,
    val effectiveAlternatives: List<ResolvedRecipe>,
    val altIndex: Int,
    val parent: Menu?
) {
    val globalFlags: Array<ItemFlag> = plugin.configYml.getStrings("item-flags")
        .mapNotNull { runCatching { ItemFlag.valueOf(it.uppercase()) }.getOrNull() }
        .toTypedArray()

    fun sound(key: String): PlayableSound? =
        plugin.configYml.getSubsectionOrNull("sounds.$key")
            ?.let { PlayableSound.create(it) }

    fun ItemStackBuilder.withGlobalFlags(): ItemStackBuilder =
        if (globalFlags.isEmpty()) this else addItemFlag(*globalFlags)
}
