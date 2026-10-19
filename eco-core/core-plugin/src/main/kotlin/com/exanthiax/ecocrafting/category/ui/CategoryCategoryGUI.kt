package com.exanthiax.ecocrafting.category.ui

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.gui.addCustomSlots
import com.willfp.eco.core.gui.addPage
import com.willfp.eco.core.gui.addPageChanger
import com.willfp.eco.core.gui.component.ConfigButton
import com.willfp.eco.core.gui.component.addComponent
import com.willfp.eco.core.gui.component.closeButton
import com.willfp.eco.core.gui.getSlotLocations
import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.page.PageChanger
import com.willfp.eco.core.gui.player
import com.willfp.eco.core.gui.setMask
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.sound.AbstractPlayableSound
import com.exanthiax.ecocrafting.category.integration.CategoryLoader
import com.exanthiax.ecocrafting.category.model.RecipeCategory
import com.exanthiax.ecocrafting.category.service.CategoryService
import com.exanthiax.ecocrafting.recipegui.service.RecipeGuiServices
import com.exanthiax.ecocrafting.recipegui.ui.utils.configSound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class CategoryCategoryGUI(
    val config: Config,
    private val categoryLoader: CategoryLoader,
    private val categoryService: CategoryService,
    private val guiServices: RecipeGuiServices
) : CategoryGUI {
    override fun open(player: Player, page: Int, prevMenu: Menu?) {
        guiServices.unlockService.checkAutoUnlocks(player)
        val maxPage = (categoryLoader.values()
            .mapNotNull { it.guiPosition?.page }
            .maxOrNull() ?: 1).coerceAtLeast(1)

        val pattern = config.getStrings("mask.pattern")

        val builtMenu = menu(pattern.size) {
            title = config.getFormattedString("title")

            maxPages(maxPage)
            // eco's defaultPage(Int) delegates to maxPages(...) instead of setting the
            // page state (upstream bug in MenuBuilder), which clobbers max-page back
            // down to whatever page we opened on. The Function overload isn't bugged.
            defaultPage { page }

            for (location in config.getSlotLocations("buttons.next-page")) {
                addPageChanger(
                    PageChanger.Direction.FORWARDS,
                    config.pageChangerItem("next-page", true),
                    config.pageChangerItem("next-page", false),
                    configSound(guiServices.plugin, "next-page"),
                    location.row,
                    location.column
                )
            }
            for (location in config.getSlotLocations("buttons.prev-page")) {
                addPageChanger(
                    PageChanger.Direction.BACKWARDS,
                    config.pageChangerItem("prev-page", true),
                    config.pageChangerItem("prev-page", false),
                    configSound(guiServices.plugin, "prev-page"),
                    location.row,
                    location.column
                )
            }

            for (pageNum in 1..maxPage) {
                addPage(pageNum) {
                    setMask(config, "")

                    config.getSubsectionOrNull("buttons.back")?.let { section ->
                        prevMenu?.let { menu ->
                            addComponent(ConfigButton(section, sound = configSound(guiServices.plugin, "back")) { player, _, _, _ ->
                                menu.open(player)
                            })
                        }
                    }

                    config.getSubsectionOrNull("buttons.close")?.let {
                        addComponent(closeButton(it, sound = configSound(guiServices.plugin, "close")))
                    }

                    addCustomSlots(config, "")

                    val positionedCategories = categoryLoader.values()
                        .filter { it.guiPosition?.page == pageNum }

                    for (category in positionedCategories) {
                        val position = category.guiPosition!!
                        val icon = category.icon?.getItemStack() ?: continue
                        setSlot(position.row, position.column, slot(icon, category, configSound(guiServices.plugin, "slot-click")))
                    }
                }
            }
        }
        builtMenu.open(player)
    }

    private fun categoryGuiFor(category: RecipeCategory): CategoryGUI {
        return if (category.type == "items") {
            ItemCategoryGUI(category.config.getSubsection("gui"), category, categoryService, guiServices)
        } else {
            CategoryCategoryGUI(category.config.getSubsection("gui"), categoryLoader, categoryService, guiServices)
        }
    }

    private fun slot(item: ItemStack, category: RecipeCategory, sound: AbstractPlayableSound<*>?): Slot {
        return Slot.builder(
            ItemStackBuilder(item.clone())
                .build()
        )
            .onLeftClick { event, _, menu ->
                categoryGuiFor(category).open(event.player, 1, menu)
                sound?.playTo(event.player)
            }
            .build()
    }
}
