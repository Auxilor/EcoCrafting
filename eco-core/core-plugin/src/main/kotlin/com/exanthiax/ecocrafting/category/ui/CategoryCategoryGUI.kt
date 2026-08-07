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
import com.willfp.eco.core.items.Items
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

            for (pageNum in 1..maxPage) {
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

                    config.getSubsectionOrNull("buttons.close")?.let {
                        addComponent(
                            config.getInt("buttons.close.row"),
                            config.getInt("buttons.close.column"),
                            closeSlot(configSound(guiServices.plugin, "close"))
                        )
                    }

                    for (slotConfig in config.getSubsections("custom-slots")) {
                        setSlot(
                            slotConfig.getInt("row"),
                            slotConfig.getInt("column"),
                            ConfigSlot(slotConfig)
                        )
                    }

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

    private fun closeSlot(sound: AbstractPlayableSound<*>?): Slot {
        return Slot.builder(
            ItemStackBuilder(Items.lookup(config.getString("buttons.close.item")))
                .addLoreLines(config.getFormattedStrings("buttons.close.lore"))
                .build()
        )
            .onLeftClick { event, _, _ ->
                event.player.closeInventory()
                sound?.playTo(event.player)
            }
            .build()
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
