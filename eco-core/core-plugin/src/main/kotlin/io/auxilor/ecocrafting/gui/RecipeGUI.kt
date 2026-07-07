package io.auxilor.ecocrafting.gui

import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.slot.ConfigSlot
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.MaskItems
import io.auxilor.ecocrafting.custom.CustomRecipes
import io.auxilor.ecocrafting.gui.utils.RecipeGUIContext
import io.auxilor.ecocrafting.gui.utils.buildBackSlot
import io.auxilor.ecocrafting.gui.utils.buildFuelSlot
import io.auxilor.ecocrafting.gui.utils.buildIndicatorSlot
import io.auxilor.ecocrafting.gui.utils.buildIngredientSlot
import io.auxilor.ecocrafting.gui.utils.buildQuickCraftSlot
import io.auxilor.ecocrafting.gui.utils.buildVariantSlot
import io.auxilor.ecocrafting.gui.utils.buildWorkstationSlot
import io.auxilor.ecocrafting.plugin
import io.auxilor.ecocrafting.recipe.RecipeDisplayType
import io.auxilor.ecocrafting.recipe.RecipeResolver
import io.auxilor.ecocrafting.recipe.RecipeSource
import io.auxilor.ecocrafting.recipe.ResolvedRecipe
import io.auxilor.ecocrafting.recipe.withLockState
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask

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
        val recipe = (effectiveAlternatives.getOrNull(altIndex)
            ?: RecipeResolver.resolveForPlayer(stack, player)
            ?: run {
                player.sendMessage(plugin.langYml.getFormattedString("messages.no-recipe"))
                return
            }).withLockState(player)

        val meta = if (recipe.source == RecipeSource.CUSTOM && recipe.key != null) {
            CustomRecipes.getMeta(recipe.key)
        } else null
        val outputLockedLore = if (recipe.locked && meta != null && meta.showWhenLocked) meta.lockedLore else emptyList()

        val context = RecipeGUIContext(
            config = plugin.configYml.getSubsection(recipe.displayType.guiSection()),
            stack = stack,
            effectiveAlternatives = effectiveAlternatives,
            altIndex = altIndex,
            parent = parent
        )

        val pattern = context.config.getStrings("mask.pattern")
        val formattedTitle = context.config.getFormattedString("title")
            .replace("%cook_time%", recipe.cookTime?.let { "${it / 20}s" } ?: "-")
            .replace("%brew_time%", recipe.brewTime?.let { "${it / 20}s" } ?: "-")
            .replace("%xp%", recipe.villagerXp?.toString() ?: "-")

        val currentTypes = effectiveAlternatives.map { it.displayType }.toSet()
        val currentType = effectiveAlternatives.getOrNull(altIndex)?.displayType
        val hasAlternatives = recipe.ingredients.any { it.allDisplayItems.size > 1 }

        var refreshTask: BukkitTask? = null

        val builtMenu = menu(pattern.size) {
            title = formattedTitle

            var ingredientIndex = 0
            pattern.forEachIndexed { rowIndex, line ->
                line.toCharArray().forEachIndexed { colIndex, marker ->
                    val row = rowIndex + 1
                    val col = colIndex + 1
                    when {
                        marker.equals('i', ignoreCase = true) -> {
                            val ingredient = recipe.ingredients.getOrNull(ingredientIndex)
                            if (ingredient != null && !ingredient.displayItem.type.isAir)
                                setSlot(row, col, context.buildIngredientSlot(ingredient.allDisplayItems, isIngredient = true, cancelRefresh = { refreshTask?.cancel() }))
                            ingredientIndex++
                        }
                        marker.equals('o', ignoreCase = true) ->
                            setSlot(row, col, context.buildIngredientSlot(listOf(recipe.output), isIngredient = false, lockedLore = outputLockedLore))
                        marker.equals('u', ignoreCase = true) ->
                            context.buildFuelSlot()?.let { setSlot(row, col, it) }
                        WORKSTATION_MARKERS.containsKey(marker) ->
                            setSlot(row, col, context.buildWorkstationSlot(marker, currentType, currentTypes))
                    }
                }
            }

            setMask(FillerMask(MaskItems.fromItemNames(context.config.getStrings("mask.items")), *pattern.toTypedArray()))

            context.config.getSubsectionOrNull("buttons.back")?.let {
                parent?.let {
                    addComponent(
                        context.config.getInt("buttons.back.row"),
                        context.config.getInt("buttons.back.column"),
                        context.buildBackSlot(parent)
                    )
                }
            }

            context.config.getSubsectionOrNull("buttons.quick-craft")
                ?.takeIf { if (context.config.has("quick-craft-enabled")) context.config.getBool("quick-craft-enabled") else true }
                ?.let {
                    addComponent(
                        context.config.getInt("buttons.quick-craft.row"),
                        context.config.getInt("buttons.quick-craft.column"),
                        context.buildQuickCraftSlot(player, recipe)
                    )
                }

            context.config.getSubsectionOrNull("buttons.crafter-indicator")
                ?.takeIf { it.getBool("enabled") }
                ?.let { indicatorConfig ->
                    val state = if (effectiveAlternatives.any { it.displayType == RecipeDisplayType.CRAFTER }) "active" else "inactive"
                    setSlot(indicatorConfig.getInt("row"), indicatorConfig.getInt("column"), context.buildIndicatorSlot(indicatorConfig, state))
                }

            context.config.getSubsectionOrNull("buttons.shapeless-indicator")
                ?.takeIf { it.getBool("enabled") }
                ?.let { indicatorConfig ->
                    val state = if (recipe.shapeless) "active" else "inactive"
                    setSlot(indicatorConfig.getInt("row"), indicatorConfig.getInt("column"), context.buildIndicatorSlot(indicatorConfig, state))
                }

            if (effectiveAlternatives.size > 1) {
                context.config.getSubsectionOrNull("buttons.prev-variant")?.let {
                    setSlot(context.config.getInt("buttons.prev-variant.row"), context.config.getInt("buttons.prev-variant.column"), context.buildVariantSlot(-1))
                }
                context.config.getSubsectionOrNull("buttons.next-variant")?.let {
                    setSlot(context.config.getInt("buttons.next-variant.row"), context.config.getInt("buttons.next-variant.column"), context.buildVariantSlot(+1))
                }
            }

            for (slotConfig in context.config.getSubsections("custom-slots")) {
                setSlot(slotConfig.getInt("row"), slotConfig.getInt("column"), ConfigSlot(slotConfig))
            }

            onClose { _, _ -> refreshTask?.cancel() }
        }

        if (hasAlternatives) {
            refreshTask = plugin.server.scheduler.runTaskTimer(
                plugin, Runnable { builtMenu.refresh(player) }, 20L, 20L
            )
        }
        builtMenu.open(player)
    }
}
