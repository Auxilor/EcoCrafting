package com.auxilor.ecocrafting.gui.utils

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.gui.slot.functional.SlotProvider
import org.bukkit.Bukkit
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import com.auxilor.ecocrafting.gui.RecipeGUI
import com.auxilor.ecocrafting.recipe.RecipeResolver
import com.auxilor.ecocrafting.ecoCraftingPlugin

fun RecipeGUIContext.buildIngredientSlot(
    items: List<ItemStack>,
    isIngredient: Boolean,
    cancelRefresh: () -> Unit = {}
): Slot = with(this) {
    fun buildDisplay(item: ItemStack): ItemStack =
        ItemStackBuilder(item.clone())
            .addLoreLines(config.getFormattedStrings("buttons.recipe-parts-lore"))
            .withGlobalFlags()
            .build()

    val clickItem = items.first()

    val slotBuilder = if (items.size <= 1) {
        Slot.builder(buildDisplay(items.first()))
    } else {
        Slot.builder(SlotProvider { _, _ ->
            val idx = (Bukkit.getCurrentTick() / 20) % items.size
            buildDisplay(items[idx])
        })
    }

    slotBuilder.onLeftClick { event, _, menu ->
        if (isIngredient) {
            val clicked = clickItem.clone().apply { amount = 1 }
            if (clicked.type != stack.type) {
                val clickedRecipe = RecipeResolver.resolve(clicked)
                if (clickedRecipe != null && RecipeResolver.canCraft(event.whoClicked as Player, clicked)) {
                    cancelRefresh()
                    RecipeGUI(clicked).open(event.whoClicked as Player, menu)
                }
            }
        }
        sound("slot-click")?.playTo(event.whoClicked as Player)
    }.build()
}

fun RecipeGUIContext.buildFuelSlot(): Slot? = with(this) {
    val fuelCfg = ecoCraftingPlugin.configYml.getSubsectionOrNull("fuel-slot") ?: return null
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
