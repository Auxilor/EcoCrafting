package ru.oftendev.recipebook.gui

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.player
import com.willfp.eco.core.gui.slot.ConfigSlot
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.MaskItems
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import net.kyori.adventure.sound.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.category.RecipeCategory
import ru.oftendev.recipebook.recipe.RecipeResolver
import ru.oftendev.recipebook.recipeBookPlugin

class ItemCategoryGUI(val config: Config, val parent: RecipeCategory): CategoryGUI {
    override fun open(player: Player, page: Int, prevMenu: Menu?) {
        val items = parent.getMemberItemsRecipes(player)
        val pattern = config.getStrings("mask.pattern")
        val menu = Menu.builder(pattern.size)
            .setTitle(config.getFormattedString("title")
                .replace("%page%", page.toString()))
        var row = 1
        var num = ((page-1)*getPerPage())
        pattern.forEach {
            var col = 1
            it.toCharArray().forEach {
                    character -> kotlin.run {
                if (character.equals('i', true)) {
                    if (num < items.size) {
                        menu.setSlot(row, col, slot(items[num], config.getStringOrNull("buttons.slot.click_sound").toAdventureSound()))
                    }
                    num++
                }
            }
                col++
            }
            row++
        }
        config.getSubsectionOrNull("buttons.back")?.let {
            prevMenu?.let {
                menu.addComponent(
                    config.getInt("buttons.back.row"),
                    config.getInt("buttons.back.column"),
                    backSlot(prevMenu,config.getStringOrNull("buttons.back.click_sound").toAdventureSound())
                )
            }
        }
        menu.setMask(
            FillerMask(
                MaskItems.fromItemNames(config.getStrings("mask.items")),
                *pattern.toTypedArray()
            )
        )
        menu.setSlot(
            config.getInt("buttons.next-page.row"),
            config.getInt("buttons.next-page.column"),
            nextSlot(page, prevMenu, player, config.getStringOrNull("buttons.next-page.click_sound").toAdventureSound())
        )
        menu.setSlot(
            config.getInt("buttons.prev-page.row"),
            config.getInt("buttons.prev-page.column"),
            prevSlot(page, prevMenu, config.getStringOrNull("buttons.prev-page.click_sound").toAdventureSound())
        )
        for (config in config.getSubsections("custom-slots")) {
            menu.setSlot(
                config.getInt("row"),
                config.getInt("column"),
                ConfigSlot(config)
            )
        }
        menu.build().open(player)
    }

    private fun backSlot(menu: Menu, sound: Sound?): Slot {
        return Slot.builder(
            ItemStackBuilder(Items.lookup(config.getString("buttons.back.item")))
                .addLoreLines(config.getFormattedStrings("buttons.back.lore"))
                .build()
        )
            .onLeftClick { t, _ ->
                menu.open(t.whoClicked as Player)
                if (sound != null) {
                    t.player.playSound(sound)
                }
            }
            .build()
    }

    private fun getPerPage(): Int {
        return config.getStrings("mask.pattern")
            .sumOf {
                it.toCharArray().filter { it1 -> it1.equals('i', true) }.size
            }
    }

    private fun getMaxPages(player: Player): Int {
        val total = parent.getMemberItemsRecipes(player).size
        return total/getPerPage() + if (total % getPerPage() > 0) 1 else 0
    }

    private fun nextSlot(page: Int, prevMenu: Menu?, player: Player, sound: Sound?): Slot {
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
                if (sound != null) {
                    event.player.playSound(sound)
                }
            }
        }
        return builder.build()
    }

    private fun prevSlot(page: Int, prevMenu: Menu?, sound: Sound?): Slot {
        val prevActive = page > 1
        val builder = Slot.builder(
            ItemStackBuilder(
                Items.lookup(config.getString("buttons.prev-page.item.${getActive(prevActive)}"))
            ).addLoreLines(
                config.getFormattedStrings("buttons.prev-page.lore.${getActive(prevActive)}")
            ).build()
        )
        if (prevActive) {
            builder.onLeftClick {
                event, _ ->
                    open(event.player, page - 1, prevMenu)
                    if (sound != null) {
                        event.player.playSound(sound)
                    }
            }
        }
        return builder.build()
    }

    private fun getActive(active: Boolean): String {
        return if (active) "active" else "inactive"
    }

    private fun slot(item: ItemStack, sound: Sound?): Slot {
        return Slot.builder(
            ItemStackBuilder(item.clone())
                .addLoreLines(config.getFormattedStrings("buttons.slot.lore"))
                .build()
        )
            .onLeftClick { event, _, menu ->
                val alternatives = RecipeResolver.resolveAll(item)
                RecipeGUI(item, alternatives, 0)
                    .open(event.whoClicked as Player, menu)
                if (sound != null) {
                    event.player.playSound(sound)
                }
            }
            .build()
    }
}

private fun String?.toAdventureSound(): Sound? =
    takeIf { !isNullOrBlank() }?.let {
        runCatching { Sound.sound(net.kyori.adventure.key.Key.key(it), Sound.Source.AMBIENT, 1.0f, 1.0f) }.getOrNull()
    }