package ru.oftendev.recipebook.gui

import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.slot.ConfigSlot
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.MaskItems
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import ru.oftendev.recipebook.gui.utils.RecipeGUIContext
import ru.oftendev.recipebook.gui.utils.buildBackSlot
import ru.oftendev.recipebook.gui.utils.buildFuelSlot
import ru.oftendev.recipebook.gui.utils.buildIndicatorSlot
import ru.oftendev.recipebook.gui.utils.buildIngredientSlot
import ru.oftendev.recipebook.gui.utils.buildPurchaseSlot
import ru.oftendev.recipebook.gui.utils.buildQuickCraftSlot
import ru.oftendev.recipebook.gui.utils.buildVariantSlot
import ru.oftendev.recipebook.gui.utils.buildWorkstationSlot
import ru.oftendev.recipebook.recipe.RecipeDisplayType
import ru.oftendev.recipebook.recipe.RecipeResolver
import ru.oftendev.recipebook.recipe.ResolvedRecipe
import ru.oftendev.recipebook.recipeBookPlugin

private fun RecipeDisplayType.guiSection() = when (this) {
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

class RecipeGUI(
    val stack: ItemStack,
    val alternatives: List<ResolvedRecipe> = emptyList(),
    val altIndex: Int = 0
) {
    fun open(player: Player, parent: Menu?) {
        val effectiveAlternatives = alternatives.ifEmpty { RecipeResolver.resolveAll(stack) }
        val recipe = effectiveAlternatives.getOrNull(altIndex)
            ?: RecipeResolver.resolveForPlayer(stack, player)
            ?: run {
                player.sendMessage(recipeBookPlugin.langYml.getFormattedString("messages.no-recipe"))
                return
            }

        val ctx = RecipeGUIContext(
            config = recipeBookPlugin.configYml.getSubsection(recipe.displayType.guiSection()),
            stack = stack,
            effectiveAlternatives = effectiveAlternatives,
            altIndex = altIndex,
            parent = parent
        )

        val pattern = ctx.config.getStrings("mask.pattern")
        val title = ctx.config.getFormattedString("title")
            .replace("%cook_time%", recipe.cookTime?.let { "${it / 20}s" } ?: "-")
            .replace("%brew_time%", recipe.brewTime?.let { "${it / 20}s" } ?: "-")
            .replace("%xp%", recipe.villagerXp?.toString() ?: "-")

        val menu = Menu.builder(pattern.size).setTitle(title)
        val currentTypes = effectiveAlternatives.map { it.displayType }.toSet()
        val currentType = effectiveAlternatives.getOrNull(altIndex)?.displayType

        var row = 1; var num = 0
        pattern.forEach { line ->
            var col = 1
            line.toCharArray().forEach { marker ->
                when {
                    marker.equals('i', true) -> {
                        val ing = recipe.ingredients.getOrNull(num)
                        if (ing != null && !ing.displayItem.type.isAir)
                            menu.setSlot(row, col, ctx.buildIngredientSlot(ing.allDisplayItems, isIngredient = true, cancelRefresh = { refreshTask?.cancel() }))
                        num++
                    }
                    marker.equals('o', true) ->
                        menu.setSlot(row, col, ctx.buildIngredientSlot(listOf(recipe.output), isIngredient = false))
                    marker.equals('u', true) ->
                        ctx.buildFuelSlot()?.let { menu.setSlot(row, col, it) }
                    WORKSTATION_MARKERS.containsKey(marker) ->
                        menu.setSlot(row, col, ctx.buildWorkstationSlot(marker, currentType, currentTypes))
                }
                col++
            }
            row++
        }

        menu.setMask(FillerMask(MaskItems.fromItemNames(ctx.config.getStrings("mask.items")), *pattern.toTypedArray()))

        ctx.config.getSubsectionOrNull("buttons.back")?.let {
            parent?.let {
                menu.addComponent(ctx.config.getInt("buttons.back.row"), ctx.config.getInt("buttons.back.column"), ctx.buildBackSlot(parent))
            }
        }

        ctx.config.getSubsectionOrNull("buttons.quick-craft")
            ?.takeIf { if (ctx.config.has("quick-craft-enabled")) ctx.config.getBool("quick-craft-enabled") else true }
            ?.let { menu.addComponent(ctx.config.getInt("buttons.quick-craft.row"), ctx.config.getInt("buttons.quick-craft.column"), ctx.buildQuickCraftSlot(player, recipe)) }

        ctx.config.getSubsectionOrNull("buttons.purchase-ingredients")
            ?.takeIf { if (ctx.config.has("buy-materials-enabled")) ctx.config.getBool("buy-materials-enabled") else true }
            ?.let { menu.addComponent(ctx.config.getInt("buttons.purchase-ingredients.row"), ctx.config.getInt("buttons.purchase-ingredients.column"), ctx.buildPurchaseSlot(player, recipe)) }

        ctx.config.getSubsectionOrNull("buttons.crafter-indicator")
            ?.takeIf { it.getBool("enabled") }
            ?.let { indCfg ->
                val state = if (effectiveAlternatives.any { it.displayType == RecipeDisplayType.CRAFTER }) "active" else "inactive"
                menu.setSlot(indCfg.getInt("row"), indCfg.getInt("column"), ctx.buildIndicatorSlot(indCfg, state))
            }

        ctx.config.getSubsectionOrNull("buttons.shapeless-indicator")
            ?.takeIf { it.getBool("enabled") }
            ?.let { indCfg ->
                val state = if (recipe.shapeless) "active" else "inactive"
                menu.setSlot(indCfg.getInt("row"), indCfg.getInt("column"), ctx.buildIndicatorSlot(indCfg, state))
            }

        if (effectiveAlternatives.size > 1) {
            ctx.config.getSubsectionOrNull("buttons.prev-variant")?.let {
                menu.setSlot(ctx.config.getInt("buttons.prev-variant.row"), ctx.config.getInt("buttons.prev-variant.column"), ctx.buildVariantSlot(-1))
            }
            ctx.config.getSubsectionOrNull("buttons.next-variant")?.let {
                menu.setSlot(ctx.config.getInt("buttons.next-variant.row"), ctx.config.getInt("buttons.next-variant.column"), ctx.buildVariantSlot(+1))
            }
        }

        for (slotConfig in ctx.config.getSubsections("custom-slots")) {
            menu.setSlot(slotConfig.getInt("row"), slotConfig.getInt("column"), ConfigSlot(slotConfig))
        }

        val hasAlternatives = recipe.ingredients.any { it.allDisplayItems.size > 1 }
        var refreshTask: BukkitTask? = null
        menu.onClose { _, _ -> refreshTask?.cancel() }
        val builtMenu = menu.build()
        if (hasAlternatives) {
            refreshTask = recipeBookPlugin.server.scheduler.runTaskTimer(
                recipeBookPlugin, Runnable { builtMenu.refresh(player) }, 20L, 20L
            )
        }
        builtMenu.open(player)
    }
}
