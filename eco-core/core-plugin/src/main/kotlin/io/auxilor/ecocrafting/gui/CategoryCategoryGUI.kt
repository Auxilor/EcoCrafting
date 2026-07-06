package io.auxilor.ecocrafting.gui

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.gui.addPage
import com.willfp.eco.core.gui.addPageChanger
import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.player
import com.willfp.eco.core.gui.page.PageChanger
import com.willfp.eco.core.gui.slot.ConfigSlot
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.MaskItems
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.sound.PlayableSound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import io.auxilor.ecocrafting.category.RecipeCategory
import io.auxilor.ecocrafting.category.RecipeCategories
import io.auxilor.ecocrafting.gui.utils.configSound

class CategoryCategoryGUI(val config: Config): CategoryGUI {
    override fun open(player: Player, page: Int, prevMenu: Menu?) {
        val maxPage = (RecipeCategories.values
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
                pageChangerItem("next-page", true),
                pageChangerItem("next-page", false),
                configSound("next-page"),
                config.getInt("buttons.next-page.row"),
                config.getInt("buttons.next-page.column")
            )
            addPageChanger(
                PageChanger.Direction.BACKWARDS,
                pageChangerItem("prev-page", true),
                pageChangerItem("prev-page", false),
                configSound("prev-page"),
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
                                backSlot(prevMenu, configSound("back"))
                            )
                        }
                    }

                    config.getSubsectionOrNull("buttons.close")?.let {
                        addComponent(
                            config.getInt("buttons.close.row"),
                            config.getInt("buttons.close.column"),
                            closeSlot(configSound("close"))
                        )
                    }

                    for (slotConfig in config.getSubsections("custom-slots")) {
                        setSlot(
                            slotConfig.getInt("row"),
                            slotConfig.getInt("column"),
                            ConfigSlot(slotConfig)
                        )
                    }

                    val positionedCategories = RecipeCategories.values
                        .filter { it.guiPosition?.page == pageNum }

                    for (category in positionedCategories) {
                        val pos = category.guiPosition!!
                        val icon = category.icon?.getItemStack() ?: continue
                        setSlot(pos.row, pos.column, slot(icon, category, configSound("slot-click")))
                    }
                }
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

    private fun closeSlot(sound: PlayableSound?): Slot {
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

    private fun pageChangerItem(base: String, active: Boolean): ItemStack {
        val state = getActive(active)
        return ItemStackBuilder(
            Items.lookup(config.getString("buttons.$base.item.$state"))
        ).addLoreLines(
            config.getFormattedStrings("buttons.$base.lore.$state")
        ).build()
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