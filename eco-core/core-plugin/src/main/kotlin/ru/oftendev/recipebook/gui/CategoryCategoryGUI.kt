package ru.oftendev.recipebook.gui

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
import ru.oftendev.recipebook.category.RecipeCategory
import ru.oftendev.recipebook.category.RecipeCategories
import ru.oftendev.recipebook.gui.utils.configSound
import ru.oftendev.recipebook.recipeBookPlugin

class CategoryCategoryGUI(val config: Config): CategoryGUI {
    override fun open(player: Player, page: Int, prevMenu: Menu?) {
        val positionedCategories = RecipeCategories.REGISTRY
            .filter { it.guiPosition != null && it.guiPosition!!.page == page }

        val maxPage = RecipeCategories.REGISTRY
            .mapNotNull { it.guiPosition?.page }
            .maxOrNull() ?: 1

        val pattern = config.getStrings("mask.pattern")

        val builtMenu = menu(pattern.size) {
            title = config.getFormattedString("title").replace("%page%", page.toString())

            setMask(
                FillerMask(
                    MaskItems.fromItemNames(config.getStrings("mask.items")),
                    *pattern.toTypedArray()
                )
            )

            for (category in positionedCategories) {
                val pos = category.guiPosition!!
                val icon = category.icon?.getItemStack() ?: continue
                setSlot(pos.row, pos.column, slot(icon, category, configSound("slot-click")))
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
                nextSlot(page, maxPage, prevMenu, configSound("next-page"))
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

    private fun nextSlot(page: Int, maxPage: Int, prevMenu: Menu?, sound: PlayableSound?): Slot {
        val nextActive = page < maxPage
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

    private fun slot(item: ItemStack, category: RecipeCategory, sound: PlayableSound?): Slot {
        return Slot.builder(
            ItemStackBuilder(item.clone())
                .build()
        )
            .onLeftClick { event, _, menu ->
                category.gui.open(event.player, 1, menu)
                sound?.playTo(event.player)
            }
            .build()
    }
}