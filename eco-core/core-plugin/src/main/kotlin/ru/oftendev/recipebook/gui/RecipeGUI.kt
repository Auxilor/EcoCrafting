package ru.oftendev.recipebook.gui

import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.player
import com.willfp.eco.core.gui.slot.ConfigSlot
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.MaskItems
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.sound.PlayableSound
import com.willfp.eco.util.formatEco
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.craft.MaterialCount
import ru.oftendev.recipebook.craft.QuickCraftService
import ru.oftendev.recipebook.integration.ShopIntegration
import ru.oftendev.recipebook.recipe.RecipeDisplayType
import ru.oftendev.recipebook.recipe.RecipeResolver
import ru.oftendev.recipebook.recipe.ResolvedRecipe
import ru.oftendev.recipebook.recipeBookPlugin

class RecipeGUI(
    val stack: ItemStack,
    val alternatives: List<ResolvedRecipe> = emptyList(),
    val altIndex: Int = 0
) {
    private lateinit var config: com.willfp.eco.core.config.interfaces.Config

    fun open(player: Player, parent: Menu?) {
        val effectiveAlternatives = if (alternatives.isEmpty()) RecipeResolver.resolveAll(stack) else alternatives
        val recipe = effectiveAlternatives.getOrNull(altIndex)
            ?: RecipeResolver.resolveForPlayer(stack, player)
            ?: run {
                player.sendMessage(recipeBookPlugin.langYml.getFormattedString("messages.no-recipe"))
                return
            }

        val items = recipe.displayItems
        val guiSection = when (recipe.displayType) {
            RecipeDisplayType.CRAFTING      -> "craft-gui"
            RecipeDisplayType.SMELTING      -> "furnace-gui"
            RecipeDisplayType.BLAST_FURNACE -> "blast-furnace-gui"
            RecipeDisplayType.SMOKER        -> "smoker-gui"
            RecipeDisplayType.CAMPFIRE      -> "campfire-gui"
            RecipeDisplayType.SMITHING      -> "smithing-gui"
            RecipeDisplayType.STONECUTTER   -> "stonecutter-gui"
            RecipeDisplayType.CRAFTER       -> "craft-gui"
            RecipeDisplayType.BREWING       -> "brewing-gui"
            RecipeDisplayType.GRINDSTONE    -> "grindstone-gui"
            RecipeDisplayType.ANVIL         -> "anvil-gui"
            RecipeDisplayType.VILLAGER      -> "villager-gui"
        }
        config = recipeBookPlugin.configYml.getSubsection(guiSection)
        val pattern = config.getStrings("mask.pattern")
        val cookTimeDisplay = recipe.cookTime?.let { "${it}t (${it / 20}s)" } ?: "-"
        val menu = Menu.builder(pattern.size)
            .setTitle(config.getFormattedString("title").replace("%cook_time%", cookTimeDisplay))

        val currentTypes = effectiveAlternatives.map { it.displayType }.toSet()
        val currentType = effectiveAlternatives.getOrNull(altIndex)?.displayType

        var row = 1
        var num = 0
        pattern.forEach { line ->
            var col = 1
            line.toCharArray().forEach { marker ->
                when {
                    marker.equals('i', true) -> {
                        if (num < items.size && !items[num].type.isAir) {
                            menu.setSlot(row, col, ingredientSlot(items[num], isIngredient = true))
                        }
                        num++
                    }
                    marker.equals('o', true) -> {
                        menu.setSlot(row, col, ingredientSlot(recipe.output, isIngredient = false))
                    }
                    marker.equals('f', true) -> {
                        recipeBookPlugin.configYml.getSubsectionOrNull("fuel-slot")?.let { fuelCfg ->
                            menu.setSlot(row, col, Slot.builder(
                                ItemStackBuilder(Items.lookup(fuelCfg.getString("item")))
                                    .addLoreLines(fuelCfg.getFormattedStrings("lore"))
                                    .build()
                            ).build())
                        }
                    }
                    WORKSTATION_MARKERS.containsKey(marker) -> {
                        menu.setSlot(row, col, workstationSlot(marker, currentType, currentTypes, effectiveAlternatives, parent))
                    }
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
                    backSlot(parent)
                )
            }
        }

        config.getSubsectionOrNull("buttons.quick-craft")
            ?.takeIf { if (config.has("quick-craft-enabled")) config.getBool("quick-craft-enabled") else true }
            ?.let {
                menu.addComponent(
                    config.getInt("buttons.quick-craft.row"),
                    config.getInt("buttons.quick-craft.column"),
                    quickCraftSlot(player, recipe)
                )
            }

        config.getSubsectionOrNull("buttons.purchase-ingredients")
            ?.takeIf { if (config.has("buy-materials-enabled")) config.getBool("buy-materials-enabled") else true }
            ?.let {
                menu.addComponent(
                    config.getInt("buttons.purchase-ingredients.row"),
                    config.getInt("buttons.purchase-ingredients.column"),
                    purchaseIngredientsSlot(player, recipe)
                )
            }

        if (effectiveAlternatives.size > 1) {
            config.getSubsectionOrNull("buttons.prev-variant")?.let {
                val prevActive = altIndex > 0
                val state = if (prevActive) "active" else "inactive"
                val slotBuilder = Slot.builder(
                    ItemStackBuilder(Items.lookup(config.getString("buttons.prev-variant.item.$state")))
                        .addLoreLines(config.getFormattedStrings("buttons.prev-variant.lore"))
                        .build()
                )
                if (prevActive) {
                    slotBuilder.onLeftClick { event, _ ->
                        RecipeGUI(stack, effectiveAlternatives, altIndex - 1).open(event.whoClicked as Player, parent)
                        sound("prev-page")?.playTo(event.whoClicked as Player)
                    }
                }
                menu.setSlot(
                    config.getInt("buttons.prev-variant.row"),
                    config.getInt("buttons.prev-variant.column"),
                    slotBuilder.build()
                )
            }

            config.getSubsectionOrNull("buttons.next-variant")?.let {
                val nextActive = altIndex < effectiveAlternatives.size - 1
                val state = if (nextActive) "active" else "inactive"
                val slotBuilder = Slot.builder(
                    ItemStackBuilder(Items.lookup(config.getString("buttons.next-variant.item.$state")))
                        .addLoreLines(config.getFormattedStrings("buttons.next-variant.lore"))
                        .build()
                )
                if (nextActive) {
                    slotBuilder.onLeftClick { event, _ ->
                        RecipeGUI(stack, effectiveAlternatives, altIndex + 1).open(event.whoClicked as Player, parent)
                        sound("next-page")?.playTo(event.whoClicked as Player)
                    }
                }
                menu.setSlot(
                    config.getInt("buttons.next-variant.row"),
                    config.getInt("buttons.next-variant.column"),
                    slotBuilder.build()
                )
            }
        }

        for (slotConfig in config.getSubsections("custom-slots")) {
            menu.setSlot(slotConfig.getInt("row"), slotConfig.getInt("column"), ConfigSlot(slotConfig))
        }

        menu.build().open(player)
    }

    private fun sound(key: String): PlayableSound? =
        recipeBookPlugin.configYml.getSubsectionOrNull("sounds.$key")
            ?.let { PlayableSound.create(it) }

    private fun workstationSlot(
        marker: Char,
        currentType: RecipeDisplayType?,
        currentTypes: Set<RecipeDisplayType>,
        effectiveAlternatives: List<ResolvedRecipe>,
        parent: Menu?
    ): Slot {
        val markerTypes = WORKSTATION_MARKERS[marker]!!
        val configKey = MARKER_CONFIG_KEY[marker]!!
        val wsName = recipeBookPlugin.langYml.getString("workstation-names.$configKey")
        val state = workstationMarkerState(markerTypes, currentType, currentTypes)
        val stateKey = state.name.lowercase().replace("_", "-")

        val rawItem = recipeBookPlugin.configYml.getString("workstation-markers.$configKey.$stateKey")
        val lore = recipeBookPlugin.configYml.getFormattedStrings("workstation-markers.$configKey.lore.$stateKey")
            .map { it.replace("%workstation%", wsName) }
        val item = ItemStackBuilder(Items.lookup(rawItem.replace("%workstation%", wsName)))
            .addLoreLines(lore)
            .build()

        val slotBuilder = Slot.builder(item)
        if (state == WorkstationMarkerState.INACTIVE) {
            val targetIndex = effectiveAlternatives.indexOfFirst { markerTypes.contains(it.displayType) }
            if (targetIndex >= 0) {
                slotBuilder.onLeftClick { event, _ ->
                    RecipeGUI(stack, effectiveAlternatives, targetIndex).open(event.whoClicked as Player, parent)
                    sound("workstation-switch")?.playTo(event.whoClicked as Player)
                }
            }
        }
        return slotBuilder.build()
    }

    private fun ingredientSlot(item: ItemStack, isIngredient: Boolean): Slot {
        return Slot.builder(
            ItemStackBuilder(item.clone())
                .addLoreLines(config.getFormattedStrings("buttons.recipe-parts-lore"))
                .build()
        ).onLeftClick { event, _, menu ->
            if (isIngredient) {
                val clicked = item.clone().apply { amount = 1 }
                val clickedRecipe = RecipeResolver.resolve(clicked)
                if (clickedRecipe != null && RecipeResolver.canCraft(event.whoClicked as Player, clicked)) {
                    RecipeGUI(clicked).open(event.whoClicked as Player, menu)
                }
            }
            sound("slot-click")?.playTo(event.whoClicked as Player)
        }.build()
    }

    private fun backSlot(menu: Menu): Slot {
        return Slot.builder(
            ItemStackBuilder(Items.lookup(config.getString("buttons.back.item")))
                .addLoreLines(config.getFormattedStrings("buttons.back.lore"))
                .build()
        ).onLeftClick { event, _ ->
            menu.open(event.whoClicked as Player)
            sound("back")?.playTo(event.whoClicked as Player)
        }.build()
    }

    private fun purchaseIngredientsSlot(player: Player, recipe: ResolvedRecipe): Slot {
        val service = QuickCraftService(player, recipe)
        val materialCounts = service.getMaterialCounts()
        val hasAllMaterials = materialCounts.all { it.has >= it.needs }
        val loreLines = config.getFormattedStrings("buttons.purchase-ingredients.lore").toMutableList()
        val materialsLoreIndex = loreLines.indexOfFirst { it.contains("%materials%") }

        if (materialsLoreIndex != -1) {
            loreLines.removeAt(materialsLoreIndex)
            loreLines.addAll(materialsLoreIndex, materialCounts.map { it.toLoreLine(player) })
            if (ShopIntegration.isAutoBuyEnabled() && !hasAllMaterials) {
                loreLines.add("")
                loreLines.add("&eShift-click &7to buy missing materials")
            }
        }

        return Slot.builder(
            ItemStackBuilder(Items.lookup(config.getString("buttons.purchase-ingredients.item")))
                .addLoreLines(loreLines)
                .build()
        ).onLeftClick { event, _ ->
            val target = event.whoClicked as Player
            if (hasAllMaterials) {
                target.sendMessage(recipeBookPlugin.langYml.getFormattedString("messages.craft-sufficient"))
                sound("purchase-success")?.playTo(target)
                return@onLeftClick
            }
            if (!ShopIntegration.isEnabled()) {
                target.sendMessage(recipeBookPlugin.langYml.getFormattedString("messages.shop-disabled"))
                sound("purchase-fail")?.playTo(target)
                return@onLeftClick
            }
            val purchase = ShopIntegration.purchaseMaterials(target, QuickCraftService(target, recipe).getMissingMaterials())
            if (purchase.success) {
                target.sendMessage(recipeBookPlugin.langYml.getFormattedString("messages.craft-purchased")
                    .replace("%item%", recipe.output.type.name.lowercase().replace("_", " ")))
                sound("purchase-success")?.playTo(target)
            } else {
                target.sendMessage(recipeBookPlugin.langYml.getFormattedString("messages.craft-failed")
                    .replace("%reason%", purchase.message))
                sound("purchase-fail")?.playTo(target)
            }
        }.onShiftLeftClick { event, _ ->
            val target = event.whoClicked as Player
            if (!ShopIntegration.canAutoBuy(true)) {
                target.sendMessage(recipeBookPlugin.langYml.getFormattedString("messages.shop-disabled"))
                return@onShiftLeftClick
            }
            val purchase = ShopIntegration.purchaseMaterials(target, QuickCraftService(target, recipe).getMissingMaterials())
            if (purchase.success) {
                sound("purchase-success")?.playTo(target)
                target.sendMessage(recipeBookPlugin.langYml.getFormattedString("messages.craft-purchased")
                    .replace("%item%", recipe.output.type.name.lowercase().replace("_", " ")))
            } else {
                sound("purchase-fail")?.playTo(target)
                target.sendMessage(recipeBookPlugin.langYml.getFormattedString("messages.craft-failed")
                    .replace("%reason%", purchase.message))
            }
        }.build()
    }

    private fun quickCraftSlot(player: Player, recipe: ResolvedRecipe): Slot {
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

        fun finishQuickCraft(event: InventoryClickEvent, target: Player, result: ru.oftendev.recipebook.craft.CraftAttempt, purchasedMaterials: Boolean) {
            if (result.success) {
                val key = if (purchasedMaterials) "messages.craft-purchased" else "messages.craft-success"
                target.sendMessage(
                    recipeBookPlugin.langYml.getFormattedString(key)
                        .replace("%item%", recipe.output.type.name.lowercase().replace("_", " "))
                )
                sound("quick-craft-success")?.playTo(target)
                target.closeInventory()
            } else {
                val key = if (result.reason == "No inventory space") "messages.craft-no-space" else "messages.craft-failed"
                target.sendMessage(
                    recipeBookPlugin.langYml.getFormattedString(key)
                        .replace("%reason%", result.reason)
                        .formatEco(target)
                )
                sound("quick-craft-fail")?.playTo(target)
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
