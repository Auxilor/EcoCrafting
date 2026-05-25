package com.auxilor.ecocrafting.gui

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.player
import com.willfp.eco.core.gui.slot.ConfigSlot
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.MaskItems
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.sound.PlayableSound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import com.auxilor.ecocrafting.category.RecipeCategory
import com.auxilor.ecocrafting.gui.utils.configSound
import com.auxilor.ecocrafting.recipe.RecipeResolver
import com.auxilor.ecocrafting.EcoCraftingPlugin

class ItemCategoryGUI(val config: Config, val parent: RecipeCategory): CategoryGUI {
    override fun open(player: Player, page: Int, prevMenu: Menu?) {
        val items = parent.getMemberItemsRecipes(player)
        val pattern = config.getStrings("mask.pattern")
        var num = ((page - 1) * getPerPage())

        val builtMenu = menu(pattern.size) {
            title = config.getFormattedString("title").replace("%page%", page.toString())

            setMask(
                FillerMask(
                    MaskItems.fromItemNames(config.getStrings("mask.items")),
                    *pattern.toTypedArray()
                )
            )

            pattern.forEachIndexed { rowIndex, line ->
                line.toCharArray().forEachIndexed { colIndex, character ->
                    if (character.equals('i', ignoreCase = true)) {
                        if (num < items.size) {
                            setSlot(rowIndex + 1, colIndex + 1, slot(items[num], configSound("slot-click")))
                        }
                        num++
                    }
                }
            }

            config.getSubsectionOrNull("buttons.back")?.let {
                prevMenu?.let {
                    addComponent(
                        config.getInt("buttons.back.row"),
                        config.getInt("buttons.back.column"),
                        backSlot(prevMenu, configSound("back"))
                    )
                }
            }

            setSlot(
                config.getInt("buttons.next-page.row"),
                config.getInt("buttons.next-page.column"),
                nextSlot(page, prevMenu, player, configSound("next-page"))
            )
            setSlot(
                config.getInt("buttons.prev-page.row"),
                config.getInt("buttons.prev-page.column"),
                prevSlot(page, prevMenu, configSound("prev-page"))
            )

            for (slotConfig in config.getSubsections("custom-slots")) {
                setSlot(
                    slotConfig.getInt("row"),
                    slotConfig.getInt("column"),
                    ConfigSlot(slotConfig)
                )
            }
        }
        builtMenu.open(player)
    }

    private fun backSlot(menu: Menu, sound: PlayableSound?): Slot {
        return Slot.builder(
            ItemStackBuilder(Items.lookup(config.getString("buttons.back.item")))
                .addLoreLines(config.getFormattedStrings("buttons.back.lore"))
                .build()
        )
            .onLeftClick { t, _ ->
                menu.open(t.whoClicked as Player)
                sound?.playTo(t.whoClicked as Player)
            }
            .build()
    }

    private fun getPerPage(): Int {
        return config.getStrings("mask.pattern")
            .sumOf { line ->
                line.count { it.equals('i', ignoreCase = true) }
            }
    }

    private fun getMaxPages(player: Player): Int {
        val total = parent.getMemberItemsRecipes(player).size
        return total/getPerPage() + if (total % getPerPage() > 0) 1 else 0
    }

    private fun nextSlot(page: Int, prevMenu: Menu?, player: Player, sound: PlayableSound?): Slot {
        val nextActive = page < getMaxPages(player)
        val builder = Slot.builder(
            ItemStackBuilder(
                Items.lookup(config.getString("buttons.next-page.item.${getActive(nextActive)}"))
            ).addLoreLines(
                config.getFormattedStrings("buttons.next-page.lore.${getActive(nextActive)}")
            ).build()
        )
        if (nextActive) {
            builder.onLeftClick { event, _ ->
                open(event.player, page + 1, prevMenu)
                sound?.playTo(event.player)
            }
        }
        return builder.build()
    }

    private fun prevSlot(page: Int, prevMenu: Menu?, sound: PlayableSound?): Slot {
        val prevActive = page > 1
        val builder = Slot.builder(
            ItemStackBuilder(
                Items.lookup(config.getString("buttons.prev-page.item.${getActive(prevActive)}"))
            ).addLoreLines(
                config.getFormattedStrings("buttons.prev-page.lore.${getActive(prevActive)}")
            ).build()
        )
        if (prevActive) {
            builder.onLeftClick { event, _ ->
                open(event.player, page - 1, prevMenu)
                sound?.playTo(event.player)
            }
        }
        return builder.build()
    }

    private fun getActive(active: Boolean): String {
        return if (active) "active" else "inactive"
    }

    private fun slot(item: ItemStack, sound: PlayableSound?): Slot {
        return Slot.builder(
            ItemStackBuilder(item.clone())
                .addLoreLines(config.getFormattedStrings("buttons.slot.lore"))
                .build()
        )
            .onLeftClick { event, _, menu ->
                val alternatives = RecipeResolver.resolveAll(item)
                RecipeGUI(item, alternatives, 0)
                    .open(event.whoClicked as Player, menu)
                sound?.playTo(event.player)
            }
            .build()
    }
}