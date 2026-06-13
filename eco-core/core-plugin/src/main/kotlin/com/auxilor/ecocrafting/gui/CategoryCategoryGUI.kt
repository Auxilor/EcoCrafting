package com.auxilor.ecocrafting.gui

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
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import com.auxilor.ecocrafting.category.RecipeCategory
import com.auxilor.ecocrafting.category.RecipeCategories
import com.auxilor.ecocrafting.gui.utils.configSound
import com.auxilor.ecocrafting.gui.utils.pageButtonItem

class CategoryCategoryGUI(val config: Config): CategoryGUI {
    override fun open(player: Player, page: Int, prevMenu: Menu?) {
        val maxPage = RecipeCategories.values
            .mapNotNull { it.guiPosition?.page }
            .maxOrNull() ?: 1

        val pattern = config.getStrings("mask.pattern")

        val builtMenu = menu(pattern.size) {
            title = config.getFormattedString("title")

            maxPages(maxPage)

            pageButtonItem(config, "buttons.next-page", "active")?.let { active ->
                addPageChanger(
                    PageChanger.Direction.FORWARDS,
                    active,
                    pageButtonItem(config, "buttons.next-page", "inactive"),
                    configSound("next-page"),
                    config.getInt("buttons.next-page.row"),
                    config.getInt("buttons.next-page.column")
                )
            }

            pageButtonItem(config, "buttons.prev-page", "active")?.let { active ->
                addPageChanger(
                    PageChanger.Direction.BACKWARDS,
                    active,
                    pageButtonItem(config, "buttons.prev-page", "inactive"),
                    configSound("prev-page"),
                    config.getInt("buttons.prev-page.row"),
                    config.getInt("buttons.prev-page.column")
                )
            }

            for (p in 1..maxPage) {
                addPage(p) {
                    setMask(
                        FillerMask(
                            MaskItems.fromItemNames(config.getStrings("mask.items")),
                            *pattern.toTypedArray()
                        )
                    )

                    for (category in RecipeCategories.values) {
                        val pos = category.guiPosition ?: continue
                        if (pos.page != p) continue
                        val icon = category.icon?.getItemStack() ?: continue
                        setSlot(pos.row, pos.column, slot(icon, category))
                    }

                    config.getSubsectionOrNull("buttons.back")?.let {
                        prevMenu?.let { prev ->
                            setSlot(
                                config.getInt("buttons.back.row"),
                                config.getInt("buttons.back.column"),
                                backSlot(prev)
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
                }
            }
        }
        builtMenu.open(player)
    }

    private fun backSlot(menu: Menu): Slot {
        return Slot.builder(
            ItemStackBuilder(Items.lookup(config.getString("buttons.back.item")))
                .addLoreLines(config.getFormattedStrings("buttons.back.lore"))
                .build()
        )
            .onLeftClick { event, _, _ ->
                menu.open(event.player)
                configSound("back")?.playTo(event.player)
            }
            .build()
    }

    private fun slot(item: ItemStack, category: RecipeCategory): Slot {
        return Slot.builder(item.clone())
            .onLeftClick { event, _, menu ->
                category.gui.open(event.player, 1, menu)
                configSound("slot-click")?.playTo(event.player)
            }
            .build()
    }
}
