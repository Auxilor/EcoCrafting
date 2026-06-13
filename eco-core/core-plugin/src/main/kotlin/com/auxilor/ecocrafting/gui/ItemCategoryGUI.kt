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
import com.auxilor.ecocrafting.gui.utils.configSound
import com.auxilor.ecocrafting.gui.utils.pageButtonItem
import com.auxilor.ecocrafting.recipe.RecipeResolver

class ItemCategoryGUI(val config: Config, val parent: RecipeCategory): CategoryGUI {
    override fun open(player: Player, page: Int, prevMenu: Menu?) {
        val items = parent.getMemberItemsRecipes(player)
        val pattern = config.getStrings("mask.pattern")
        val perPage = getPerPage(pattern)
        val maxPage = getMaxPages(items.size, perPage)

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

                    var num = (p - 1) * perPage
                    pattern.forEachIndexed { rowIndex, line ->
                        line.forEachIndexed { colIndex, character ->
                            if (character.equals('i', ignoreCase = true)) {
                                if (num < items.size) {
                                    setSlot(rowIndex + 1, colIndex + 1, slot(items[num]))
                                }
                                num++
                            }
                        }
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

    private fun getPerPage(pattern: List<String>): Int {
        return pattern.sumOf { line -> line.count { it.equals('i', ignoreCase = true) } }
    }

    private fun getMaxPages(itemCount: Int, perPage: Int): Int {
        if (perPage <= 0) return 1
        return ((itemCount + perPage - 1) / perPage).coerceAtLeast(1)
    }

    private fun slot(item: ItemStack): Slot {
        return Slot.builder(
            ItemStackBuilder(item.clone())
                .addLoreLines(config.getFormattedStrings("buttons.slot.lore"))
                .build()
        )
            .onLeftClick { event, _, menu ->
                val alternatives = RecipeResolver.resolveAll(item)
                RecipeGUI(item, alternatives, 0)
                    .open(event.player, menu)
                configSound("slot-click")?.playTo(event.player)
            }
            .build()
    }
}
