package ru.oftendev.recipebook.gui.utils

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.gui.RecipeGUI
import ru.oftendev.recipebook.recipe.RecipeResolver
import ru.oftendev.recipebook.recipeBookPlugin

fun RecipeGUIContext.buildIngredientSlot(item: ItemStack, isIngredient: Boolean): Slot = with(this) {
    Slot.builder(
        ItemStackBuilder(item.clone())
            .addLoreLines(config.getFormattedStrings("buttons.recipe-parts-lore"))
            .withGlobalFlags()
            .build()
    ).onLeftClick { event, _, menu ->
        if (isIngredient) {
            val clicked = item.clone().apply { amount = 1 }
            val clickedRecipe = RecipeResolver.resolve(clicked)
            if (clickedRecipe != null && RecipeResolver.canCraft(event.whoClicked as Player, clicked)) {
                RecipeGUI(clicked).open(event.whoClicked as Player, menu)
            }
        }
        sound("slot-click")?.playTo(event.whoClicked as Player)
    }.build()
}

fun RecipeGUIContext.buildFuelSlot(): Slot? = with(this) {
    val fuelCfg = recipeBookPlugin.configYml.getSubsectionOrNull("fuel-slot") ?: return null
    Slot.builder(
        ItemStackBuilder(Items.lookup(fuelCfg.getString("item")))
            .addLoreLines(fuelCfg.getFormattedStrings("lore"))
            .withGlobalFlags()
            .build()
    ).build()
}

fun RecipeGUIContext.buildBackSlot(parentMenu: Menu): Slot = with(this) {
    Slot.builder(
        ItemStackBuilder(Items.lookup(config.getString("buttons.back.item")))
            .addLoreLines(config.getFormattedStrings("buttons.back.lore"))
            .withGlobalFlags()
            .build()
    ).onLeftClick { event, _ ->
        parentMenu.open(event.whoClicked as Player)
        sound("back")?.playTo(event.whoClicked as Player)
    }.build()
}

fun RecipeGUIContext.buildIndicatorSlot(indicatorCfg: Config, state: String): Slot = with(this) {
    Slot.builder(
        ItemStackBuilder(Items.lookup(indicatorCfg.getString("item.$state")))
            .addLoreLines(indicatorCfg.getFormattedStrings("lore.$state"))
            .withGlobalFlags()
            .build()
    ).build()
}

fun RecipeGUIContext.buildVariantSlot(direction: Int): Slot = with(this) {
    val newIndex = altIndex + direction
    val active = newIndex in effectiveAlternatives.indices
    val buttonKey = if (direction < 0) "prev-variant" else "next-variant"
    val soundKey = if (direction < 0) "prev-page" else "next-page"
    val state = if (active) "active" else "inactive"
    val slotBuilder = Slot.builder(
        ItemStackBuilder(Items.lookup(config.getString("buttons.$buttonKey.item.$state")))
            .addLoreLines(config.getFormattedStrings("buttons.$buttonKey.lore"))
            .withGlobalFlags()
            .build()
    )
    if (active) slotBuilder.onLeftClick { event, _ ->
        RecipeGUI(stack, effectiveAlternatives, newIndex).open(event.whoClicked as Player, parent)
        sound(soundKey)?.playTo(event.whoClicked as Player)
    }
    slotBuilder.build()
}
