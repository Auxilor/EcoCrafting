package ru.oftendev.recipebook.gui.utils

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.sound.PlayableSound
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.recipe.ResolvedRecipe
import ru.oftendev.recipebook.recipeBookPlugin

class RecipeGUIContext(
    val config: Config,
    val stack: ItemStack,
    val effectiveAlternatives: List<ResolvedRecipe>,
    val altIndex: Int,
    val parent: Menu?
) {
    val globalFlags: Array<ItemFlag> = recipeBookPlugin.configYml.getStrings("item-flags")
        .mapNotNull { runCatching { ItemFlag.valueOf(it.uppercase()) }.getOrNull() }
        .toTypedArray()

    fun sound(key: String): PlayableSound? =
        recipeBookPlugin.configYml.getSubsectionOrNull("sounds.$key")
            ?.let { PlayableSound.create(it) }

    fun ItemStackBuilder.withGlobalFlags(): ItemStackBuilder =
        if (globalFlags.isEmpty()) this else addItemFlag(*globalFlags)
}
