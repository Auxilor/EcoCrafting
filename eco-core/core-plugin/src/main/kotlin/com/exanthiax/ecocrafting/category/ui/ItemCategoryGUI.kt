package com.exanthiax.ecocrafting.category.ui

import com.exanthiax.ecocrafting.category.model.RecipeCategory
import com.exanthiax.ecocrafting.category.service.CategoryService
import com.exanthiax.ecocrafting.recipe.model.RecipeSource
import com.exanthiax.ecocrafting.recipe.service.withLockState
import com.exanthiax.ecocrafting.recipegui.service.RecipeGuiServices
import com.exanthiax.ecocrafting.recipegui.ui.RecipeGUI
import com.exanthiax.ecocrafting.recipegui.ui.utils.configSound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import wiki.ecohub.config.Config
import wiki.ecohub.game.item.builder.ItemStackBuilder
import wiki.ecohub.game.sound.AbstractPlayableSound
import wiki.ecohub.modules.menus.addCustomSlots
import wiki.ecohub.modules.menus.addPage
import wiki.ecohub.modules.menus.addPageChanger
import wiki.ecohub.modules.menus.component.ConfigButton
import wiki.ecohub.modules.menus.component.addComponent
import wiki.ecohub.modules.menus.menu
import wiki.ecohub.modules.menus.menu.Menu
import wiki.ecohub.modules.menus.page.PageChanger
import wiki.ecohub.modules.menus.player
import wiki.ecohub.modules.menus.setMask
import wiki.ecohub.modules.menus.slot.Slot

class ItemCategoryGUI(
    val config: Config,
    val parent: RecipeCategory,
    private val categoryService: CategoryService,
    private val guiServices: RecipeGuiServices
) : CategoryGUI {
    override fun open(player: Player, page: Int, prevMenu: Menu?) {
        guiServices.unlockService.checkAutoUnlocks(player)
        open(player, page, prevMenu, categoryService.getMemberItemsRecipes(parent, player))
    }

    private fun open(player: Player, page: Int, prevMenu: Menu?, items: List<ItemStack>) {
        val pattern = config.getStrings("mask.pattern")
        val perPage = getPerPage()
        val maxPages = getMaxPages(items.size, perPage).coerceAtLeast(1)

        val builtMenu = menu(pattern.size) {
            setTitle(config.getFormattedString("title"))

            maxPages(maxPages)
            // eco's defaultPage(Int) delegates to maxPages(...) instead of setting the
            // page state (upstream bug in MenuBuilder), which clobbers max-page back
            // down to whatever page we opened on. The Function overload isn't bugged.
            defaultPage { page }

            addPageChanger(
                config,
                "buttons.next-page",
                PageChanger.Direction.FORWARDS,
                configSound(guiServices.plugin, "next-page")
            )
            addPageChanger(
                config,
                "buttons.prev-page",
                PageChanger.Direction.BACKWARDS,
                configSound(guiServices.plugin, "prev-page")
            )

            for (pageNum in 1..maxPages) {
                addPage(pageNum) {
                    setMask(config, "")

                    val backSection = config.getSubsectionOrNull("buttons.back")
                    if (backSection != null && prevMenu != null) {
                        addComponent(
                            ConfigButton(
                                backSection,
                                sound = configSound(guiServices.plugin, "back")
                            ) { clicker, _, _, _ ->
                                prevMenu.open(clicker)
                            }
                        )
                    }

                    addCustomSlots(config, "")

                    var itemIndex = (pageNum - 1) * perPage
                    pattern.forEachIndexed { rowIndex, line ->
                        line.toCharArray().forEachIndexed { colIndex, character ->
                            if (character.equals('i', ignoreCase = true)) {
                                if (itemIndex < items.size) {
                                    setSlot(
                                        row = rowIndex + 1,
                                        column = colIndex + 1,
                                        slot = slot(
                                            player,
                                            items[itemIndex],
                                            configSound(guiServices.plugin, "slot-click")
                                        )
                                    )
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
