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
import com.willfp.eco.util.formatEco
import net.kyori.adventure.sound.Sound
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.craft.MaterialCount
import ru.oftendev.recipebook.craft.QuickCraftService
import ru.oftendev.recipebook.integration.ShopIntegration
import ru.oftendev.recipebook.makeSound
import ru.oftendev.recipebook.recipe.RecipeResolver
import ru.oftendev.recipebook.recipe.ResolvedRecipe
import ru.oftendev.recipebook.recipeBookPlugin

class RecipeGUI(val config: Config, val stack: ItemStack) {
    fun open(player: Player, parent: Menu?) {
        val recipe = RecipeResolver.resolve(stack) ?: run {
            player.sendMessage(recipeBookPlugin.langYml.getFormattedString("messages.no-recipe"))
            return
        }
        val items = recipe.displayItems
        val pattern = config.getStrings("mask.pattern")
        val menu = Menu.builder(pattern.size)
            .setTitle(config.getFormattedString("title"))

        var row = 1
        var num = 0
        pattern.forEach { line ->
            var col = 1
            line.toCharArray().forEach { marker ->
                if (marker.equals('i', true)) {
                    if (num < items.size && !items[num].type.isAir) {
                        menu.setSlot(row, col, slot(items[num], makeSound(config.getStringOrNull("buttons.back.click_sound")), true))
                    }
                    num++
                }
                if (marker.equals('o', true)) {
                    menu.setSlot(row, col, slot(recipe.output, makeSound(config.getStringOrNull("buttons.slot.click_sound")), false))
                }
                col++
            }
            row++
        }

        menu.setMask(FillerMask(MaskItems.fromItemNames(config.getStrings("mask.items")), *pattern.toTypedArray()))

        config.getSubsectionOrNull("buttons.back")?.let {
            parent?.let {
                menu.addComponent(
                    config.getInt("buttons.back.row"),
                    config.getInt("buttons.back.column"),
                    backSlot(parent, makeSound(config.getStringOrNull("buttons.back.click_sound")))
                )
            }
        }

        config.getSubsectionOrNull("buttons.quick-craft")?.let {
            menu.addComponent(
                config.getInt("buttons.quick-craft.row"),
                config.getInt("buttons.quick-craft.column"),
                quickCraftSlot(
                    player,
                    recipe,
                    makeSound(config.getStringOrNull("buttons.quick-craft.success_sound")),
                    makeSound(config.getStringOrNull("buttons.quick-craft.fail_sound"))
                )
            )
        }

        for (slotConfig in config.getSubsections("custom-slots")) {
            menu.setSlot(slotConfig.getInt("row"), slotConfig.getInt("column"), ConfigSlot(slotConfig))
        }

        menu.build().open(player)
    }

    private fun backSlot(menu: Menu, sound: Sound?): Slot {
        return Slot.builder(
            ItemStackBuilder(Items.lookup(config.getString("buttons.back.item")))
                .addLoreLines(config.getFormattedStrings("buttons.back.lore"))
                .build()
        ).onLeftClick { event, _ ->
            menu.open(event.whoClicked as Player)
            sound?.let { event.player.playSound(it) }
        }.build()
    }

    private fun slot(item: ItemStack, sound: Sound?, recipe: Boolean): Slot {
        return Slot.builder(
            ItemStackBuilder(item.clone())
                .addLoreLines(config.getFormattedStrings("buttons.recipe-parts-lore"))
                .build()
        ).onLeftClick { event, _, menu ->
            val clicked = item.clone().apply { amount = 1 }
            val clickedRecipe = RecipeResolver.resolve(clicked)
            if (recipe && clickedRecipe != null && RecipeResolver.canCraft(event.whoClicked as Player, clicked)) {
                RecipeGUI(recipeBookPlugin.configYml.getSubsection("craft-gui"), clicked)
                    .open(event.whoClicked as Player, menu)
            }
            sound?.let { event.player.playSound(it) }
        }.build()
    }

    private fun quickCraftSlot(player: Player, recipe: ResolvedRecipe, successSound: Sound?, failSound: Sound?): Slot {
        val service = QuickCraftService(player, recipe)
        val materialCounts = service.getMaterialCounts()
        val hasAllMaterials = materialCounts.all { it.has >= it.needs }
        val loreLines = config.getFormattedStrings("buttons.quick-craft.lore").toMutableList()
        val materialsLoreIndex = loreLines.indexOfFirst { it.contains("%materials%") }

        if (materialsLoreIndex != -1) {
            loreLines.removeAt(materialsLoreIndex)
            loreLines.addAll(materialsLoreIndex, materialCounts.map { it.toLoreLine(player) })
            if (ShopIntegration.isAutoBuyEnabled() && !hasAllMaterials) {
                loreLines.add("")
                loreLines.add("&eShift-click &7to buy missing materials and craft")
            }
        }

        fun finishQuickCraft(
            event: InventoryClickEvent,
            target: Player,
            result: ru.oftendev.recipebook.craft.CraftAttempt,
            purchasedMaterials: Boolean
        ) {
            if (result.success) {
                val key = if (purchasedMaterials) "messages.craft-purchased" else "messages.craft-success"
                target.sendMessage(
                    recipeBookPlugin.langYml.getFormattedString(key)
                        .replace("%item%", recipe.output.type.name.lowercase().replace("_", " "))
                )
                successSound?.let { event.player.playSound(it) }
                target.closeInventory()
            } else {
                val key = if (result.reason == "No inventory space") "messages.craft-no-space" else "messages.craft-failed"
                target.sendMessage(
                    recipeBookPlugin.langYml.getFormattedString(key)
                        .replace("%reason%", result.reason)
                        .formatEco(target)
                )
                failSound?.let { event.player.playSound(it) }
            }
        }

        fun schedulePurchasedCraftAttempt(event: InventoryClickEvent, target: Player, attempts: Int = 0) {
            Bukkit.getScheduler().runTaskLater(recipeBookPlugin, Runnable {
                val delayedResult = QuickCraftService(target, recipe).craft()
                if (delayedResult.success || delayedResult.reason != "Missing materials" || attempts >= 20) {
                    finishQuickCraft(event, target, delayedResult, true)
                } else {
                    schedulePurchasedCraftAttempt(event, target, attempts + 1)
                }
            }, 1L)
        }

        fun handleQuickCraft(event: InventoryClickEvent) {
            val target = event.whoClicked as Player
            val liveService = QuickCraftService(target, recipe)
            var result = liveService.craft()

            if (!result.success && result.reason == "Missing materials") {
                if (ShopIntegration.canAutoBuy(event.isShiftClick)) {
                    val purchase = ShopIntegration.purchaseMaterials(target, liveService.getMissingMaterials())
                    if (purchase.success) {
                        schedulePurchasedCraftAttempt(event, target)
                        return
                    }
                    result = result.copy(reason = purchase.message)
                } else if (event.isShiftClick && ShopIntegration.isEnabled()) {
                    result = result.copy(reason = "EcoShop auto-purchase is disabled in RecipeBook config")
                }
            }

            finishQuickCraft(event, target, result, false)
        }

        return Slot.builder(
            ItemStackBuilder(Items.lookup(config.getString("buttons.quick-craft.item")))
                .addLoreLines(loreLines)
                .build()
        ).onLeftClick { event, _ ->
            handleQuickCraft(event)
        }.onShiftLeftClick { event, _ ->
            handleQuickCraft(event)
        }.build()
    }

    private fun MaterialCount.toLoreLine(player: Player): String {
        val color = if (has >= needs) "&a" else "&c"
        val itemName = if (item.hasItemMeta() && item.itemMeta.hasDisplayName()) {
            item.itemMeta.displayName
        } else {
            item.type.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }
        }
        var line = "$color  $has/$needs &7$itemName"
        if (has < needs && ShopIntegration.shouldShowPrices()) {
            val info = ShopIntegration.getMaterialShopInfo(player, item, needs - has)
            if (info != null) {
                val shopColor = if (info.canBuy) "&e" else "&c"
                line += " $shopColor(${info.priceDisplay.ifBlank { info.status }})"
            }
        }
        return line
    }
}
