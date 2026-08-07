package com.exanthiax.ecocrafting.category.ui

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.gui.addPage
import com.willfp.eco.core.gui.addPageChanger
import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.page.PageChanger
import com.willfp.eco.core.gui.player
import com.willfp.eco.core.gui.slot.ConfigSlot
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.MaskItems
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.sound.AbstractPlayableSound
import com.exanthiax.ecocrafting.category.model.RecipeCategory
import com.exanthiax.ecocrafting.category.service.CategoryService
import com.exanthiax.ecocrafting.recipe.model.RecipeSource
import com.exanthiax.ecocrafting.recipe.service.withLockState
import com.exanthiax.ecocrafting.recipegui.service.RecipeGuiServices
import com.exanthiax.ecocrafting.recipegui.ui.RecipeGUI
import com.exanthiax.ecocrafting.recipegui.ui.utils.configSound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class ItemCategoryGUI(
    val config: Config,
    val parent: RecipeCategory,
    private val categoryService: CategoryService,
    private val guiServices: RecipeGuiServices
) : CategoryGUI {
    override fun open(player: Player, page: Int, prevMenu: Menu?) {
        open(player, page, prevMenu, categoryService.getMemberItemsRecipes(parent, player))
    }

    private fun open(player: Player, page: Int, prevMenu: Menu?, items: List<ItemStack>) {
        val pattern = config.getStrings("mask.pattern")
        val perPage = getPerPage()
        val maxPages = getMaxPages(items.size, perPage).coerceAtLeast(1)

        val builtMenu = menu(pattern.size) {
            title = config.getFormattedString("title")

            maxPages(maxPages)
            // eco's defaultPage(Int) delegates to maxPages(...) instead of setting the
            // page state (upstream bug in MenuBuilder), which clobbers max-page back
            // down to whatever page we opened on. The Function overload isn't bugged.
            defaultPage { page }

            addPageChanger(
                PageChanger.Direction.FORWARDS,
                config.pageChangerItem("next-page", true),
                config.pageChangerItem("next-page", false),
                configSound(guiServices.plugin, "next-page"),
                config.getInt("buttons.next-page.row"),
                config.getInt("buttons.next-page.column")
            )
            addPageChanger(
                PageChanger.Direction.BACKWARDS,
                config.pageChangerItem("prev-page", true),
                config.pageChangerItem("prev-page", false),
                configSound(guiServices.plugin, "prev-page"),
                config.getInt("buttons.prev-page.row"),
                config.getInt("buttons.prev-page.column")
            )

            for (pageNum in 1..maxPages) {
                addPage(pageNum) {
                    setMask(
                        FillerMask(
                            MaskItems.fromItemNames(config.getStrings("mask.items")),
                            *pattern.toTypedArray()
                        )
                    )

                    config.getSubsectionOrNull("buttons.back")?.let {
                        prevMenu?.let {
                            addComponent(
                                config.getInt("buttons.back.row"),
                                config.getInt("buttons.back.column"),
                                config.backSlot(prevMenu, configSound(guiServices.plugin, "back"))
                            )
                        }
                    }

                    for (slotConfig in config.getSubsections("custom-slots")) {
                        setSlot(
                            slotConfig.getInt("row"),
                            slotConfig.getInt("column"),
                            ConfigSlot(slotConfig)
                        )
                    }

                    var itemIndex = (pageNum - 1) * perPage
                    pattern.forEachIndexed { rowIndex, line ->
                        line.toCharArray().forEachIndexed { colIndex, character ->
                            if (character.equals('i', ignoreCase = true)) {
                                if (itemIndex < items.size) {
                                    setSlot(rowIndex + 1, colIndex + 1, slot(player, items[itemIndex], configSound(guiServices.plugin, "slot-click")))
                                }
                                itemIndex++
                            }
                        }
                    }
                }
            }
        }
        builtMenu.open(player)
    }

    private fun getPerPage(): Int {
        return config.getStrings("mask.pattern")
            .sumOf { line ->
                line.count { it.equals('i', ignoreCase = true) }
            }
    }

    private fun getMaxPages(itemCount: Int, perPage: Int): Int {
        if (perPage <= 0) return 0
        return itemCount / perPage + if (itemCount % perPage > 0) 1 else 0
    }

    private fun slot(player: Player, item: ItemStack, sound: AbstractPlayableSound<*>?): Slot {
        // Cached single-recipe lookup for the slot's own lore/lock display - resolveAll's
        // full alternatives list is only needed once the player actually opens the recipe.
        val resolved = guiServices.resolverService.resolve(item)
            ?.withLockState(player, guiServices.recipeService, guiServices.unlockService)
        val meta = if (resolved?.source == RecipeSource.CUSTOM && resolved.key != null) {
            guiServices.recipeService.getMeta(resolved.key)
        } else null
        val loreLines = config.getFormattedStrings("buttons.slot.lore").let { base ->
            if (resolved?.locked == true && meta != null && meta.showWhenLocked && meta.lockedLore.isNotEmpty()) {
                base + meta.lockedLore
            } else {
                base
            }
        }

        return Slot.builder(
            ItemStackBuilder(item.clone())
                .addLoreLines(loreLines)
                .build()
        )
            .onLeftClick { event, _, menu ->
                val alternatives = guiServices.resolverService.resolveAll(item)
                RecipeGUI(guiServices, item, alternatives, 0)
                    .open(event.whoClicked as Player, menu)
                sound?.playTo(event.player)
            }
            .build()
    }
}
