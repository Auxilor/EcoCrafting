package com.exanthiax.ecocrafting.recipegui.ui.utils

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.gui.slot.functional.SlotProvider
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.exanthiax.ecocrafting.recipegui.ui.RecipeGUI
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

// Identity check for the "don't re-open the recipe already on screen" guard. Comparing
// Material alone collapsed every custom item sharing a base material - all player_head
// backed EcoItems, say - so click-through to an ingredient's own recipe silently did
// nothing whenever it happened to share a material with the item being viewed.
internal fun isSameItemAs(clicked: ItemStack, viewing: ItemStack): Boolean {
    val clickedCustom = Items.getCustomItem(clicked)
    val viewingCustom = Items.getCustomItem(viewing)
    if (clickedCustom != null || viewingCustom != null) return clickedCustom?.key == viewingCustom?.key
    return clicked.isSimilar(viewing)
}

fun RecipeGUIContext.buildIngredientSlot(
    items: List<ItemStack>,
    isIngredient: Boolean,
    lockedLore: List<String> = emptyList(),
    cancelRefresh: () -> Unit = {}
): Slot = with(this) {
    fun buildDisplay(item: ItemStack): ItemStack =
        ItemStackBuilder(item.clone())
            .apply {
                if (isIngredient) addLoreLines(config.getFormattedStrings("buttons.recipe-parts-lore"))
                if (lockedLore.isNotEmpty()) addLoreLines(lockedLore)
            }
            .withGlobalFlags()
            .build()

    val clickItem = items.first()

    val slotBuilder = if (items.size <= 1) {
        Slot.builder(buildDisplay(items.first()))
    } else {
        Slot.builder(SlotProvider { _, _ ->
            val index = (Bukkit.getCurrentTick() / 20) % items.size
            buildDisplay(items[index])
        })
    }

    slotBuilder.onLeftClick { event, _, menu ->
        if (isIngredient) {
            val clicked = clickItem.clone().apply { amount = 1 }
            if (!isSameItemAs(clicked, stack)) {
                val clickedRecipe = services.resolverService.resolve(clicked)
                if (clickedRecipe != null && services.resolverService.canCraft(event.whoClicked as Player, clicked)) {
                    cancelRefresh()
                    RecipeGUI(services, clicked).open(event.whoClicked as Player, menu)
                }
            }
        }
        sound("slot-click")?.playTo(event.whoClicked as Player)
    }.build()
}

fun RecipeGUIContext.buildFuelSlot(): Slot? = with(this) {
    val fuelCfg = services.plugin.configYml.getSubsectionOrNull("fuel-slot") ?: return null
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

fun RecipeGUIContext.buildIndicatorSlot(indicatorConfig: Config, state: String): Slot = with(this) {
    Slot.builder(
        ItemStackBuilder(Items.lookup(indicatorConfig.getString("item.$state")))
            .addLoreLines(indicatorConfig.getFormattedStrings("lore.$state"))
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
        RecipeGUI(services, stack, effectiveAlternatives, newIndex).open(event.whoClicked as Player, parent)
        sound(soundKey)?.playTo(event.whoClicked as Player)
    }
    slotBuilder.build()
}
