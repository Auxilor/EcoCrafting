package com.exanthiax.ecocrafting.crafting.integration

import com.willfp.eco.core.recipe.workstation.CrafterRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.crafting.event.CustomCraftEvent
import com.exanthiax.ecocrafting.crafting.service.checkCraftingConditions
import com.exanthiax.ecocrafting.crafting.service.fireCraftEffects
import com.exanthiax.ecocrafting.crafting.service.priceAffordableAmount
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.inventory.SmithingInventory
import org.bukkit.inventory.StonecutterInventory
import org.bukkit.inventory.ItemStack

// Eco registers a crafting recipe's Bukkit twin with material-only ingredients, so recipes
// that differ only by which custom item a part tests for (several player_head-backed
// EcoItems, say) collapse onto one Bukkit recipe and CraftItemEvent can name any of them.
// The grid is the authority, so only trust the event's key when the two agree; a
// disagreement drops back to the matrix match, which takes the craft over manually.
internal fun trustedEventRecipe(fromEvent: CrafterRecipe?, fromGrid: CrafterRecipe?): CrafterRecipe? =
    fromEvent?.takeIf { fromGrid == null || it.key == fromGrid.key }

// Crafting table / Crafter block craft handling. CraftItemEvent is authoritative here
// (topInventory really is a CraftingInventory), unlike the stonecutter/smithing table
// where it's only an informational event and the real gate is an InventoryClickEvent -
// see StonecutterListener/SmithingListener. The type guard below is just defensive.
class CraftingTableListener(
    private val plugin: EcoCraftingPlugin,
    private val recipeService: RecipeService,
    private val unlockService: RecipeUnlockService
) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPrepareCraft(event: PrepareItemCraftEvent) {
        val recipe = findCraftingTableRecipe(event.inventory.matrix) ?: return
        val output = recipe.output?.clone() ?: return
        val player = event.view.player as? Player

        val meta = recipeService.getMeta(recipe.key)
        if (player != null && meta != null && meta.showWhenLocked && unlockService.isLocked(player, recipe.key, meta)) {
            output.itemMeta = output.itemMeta?.apply {
                lore = (lore ?: mutableListOf()).apply { addAll(meta.lockedLore) }
            }
        }

        event.inventory.result = output
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        if (event.view.topInventory is StonecutterInventory || event.view.topInventory is SmithingInventory) return

        val player = event.whoClicked as? Player ?: return
        val recipeKey = (event.recipe as? org.bukkit.Keyed)?.key ?: return

        val matrixMatch = findCraftingTableRecipe(event.inventory.matrix)
        val directMatch = trustedEventRecipe(
            WorkstationRecipes.getByKey(recipeService.baseKeyForVariant(recipeKey)) as? CrafterRecipe,
            matrixMatch
        )
        // Fall back to matrix match when a vanilla recipe fires first (same shape/material),
        // then to the fully-normalized base key - this last step catches a rotated symmetry
        // variant fired under its `_displayed`/`_crafter` key, whose rotated matrix won't
        // match the base orientation above. It routes through the manual-takeover path
        // (needsTakeover stays true) just like the base recipe, so it can't skip the
        // lock/price/condition checks below.
        val recipe = directMatch
            ?: matrixMatch
            ?: (WorkstationRecipes.getByKey(recipeService.resolveBaseKey(recipeKey)) as? CrafterRecipe)
            ?: return
        val needsTakeover = directMatch == null
        val meta = recipeService.getMeta(recipe.key) ?: return
        if (!checkCraftingConditions(plugin, unlockService, player, recipe, meta)) {
            event.isCancelled = true
            if (meta.showWhenLocked && unlockService.isLocked(player, recipe.key, meta)) {
                recipe.output?.clone()?.let { lockedOutput ->
                    lockedOutput.itemMeta = lockedOutput.itemMeta?.apply {
                        lore = (lore ?: mutableListOf()).apply { addAll(meta.lockedLore) }
                    }
                    event.inventory.result = lockedOutput
                }
            }
            plugin.server.scheduler.runTask(plugin, Runnable { player.updateInventory() })
            return
        }

        val amount = priceAffordableAmount(player, meta.price, calculateCraftAmount(plugin, event, maxCraftsFromGrid(event.inventory.matrix))).coerceAtLeast(1)
        val item = recipe.output?.clone()?.apply { this.amount = amount } ?: return

        val customEvent = CustomCraftEvent(player, recipe, item, amount)
        Bukkit.getPluginManager().callEvent(customEvent)
        if (customEvent.isCancelled) { event.isCancelled = true; return }

        // support-crafter recipes also register a second, ambiguous Bukkit recipe
        // (RecipeChoice.ExactChoice on the same shape) so the vanilla Crafter block
        // can match them. That duplicate breaks vanilla's own shift-click "craft all"
        // repeat loop at a normal crafting table, so those recipes always take over
        // the craft manually instead of trusting the vanilla native path.
        val tookOver = (needsTakeover || meta.supportCrafter) && meta.giveResultItem
        meta.price.pay(player, amount.toDouble())
        when {
            !meta.giveResultItem -> {
                event.isCancelled = true
                consumeCraftingGrid(event, amount)
            }
            tookOver -> {
                event.isCancelled = true
                consumeCraftingGrid(event, amount)
                giveCraftedItem(event, player, item)
            }
        }
        fireCraftEffects(player, recipe, meta, item, amount, event.view.topInventory.location?.block)
    }

    private fun giveCraftedItem(event: CraftItemEvent, player: Player, item: ItemStack) {
        if (event.isShiftClick) {
            val overflow = player.inventory.addItem(item)
            overflow.values.forEach { player.world.dropItemNaturally(player.location, it) }
            return
        }
        val cursor = event.cursor
        when {
            cursor == null || cursor.type.isAir -> player.setItemOnCursor(item)
            cursor.isSimilar(item) && cursor.amount + item.amount <= item.maxStackSize -> {
                cursor.amount += item.amount
                player.setItemOnCursor(cursor)
            }
            else -> {
                val overflow = player.inventory.addItem(item)
                overflow.values.forEach { player.world.dropItemNaturally(player.location, it) }
            }
        }
    }

    private fun findCraftingTableRecipe(matrix: Array<out ItemStack?>): CrafterRecipe? {
        return WorkstationRecipes.getAll(CrafterRecipe::class.java)
            .firstOrNull { recipe ->
                if (recipe.isShapeless) shapelessMatch(recipe, matrix)
                else shapedMatch(recipe, matrix)
            }
    }

    private fun shapedMatch(recipe: CrafterRecipe, matrix: Array<out ItemStack?>): Boolean {
        if (matrix.size != recipe.parts.size) return false
        return recipe.parts.zip(matrix.toList()).all { (part, slot) ->
            if (part == null) slot == null || slot.type.isAir
            else part.matches(slot)
        }
    }

    private fun shapelessMatch(recipe: CrafterRecipe, matrix: Array<out ItemStack?>): Boolean {
        val required = recipe.parts.filterNotNull().toMutableList()
        val present = matrix.filter { it != null && !it.type.isAir }
        if (required.size != present.size) return false
        for (slot in present) {
            val index = required.indexOfFirst { it.matches(slot) }
            if (index < 0) return false
            required.removeAt(index)
        }
        return required.isEmpty()
    }

    private fun consumeCraftingGrid(event: CraftItemEvent, amount: Int) {
        val matrix = event.inventory.matrix
        for (slot in matrix.indices) {
            val stack = matrix[slot] ?: continue
            if (stack.type.isAir) continue
            val remaining = stack.amount - amount
            matrix[slot] = if (remaining <= 0) null else stack.apply { this.amount = remaining }
        }
        event.inventory.matrix = matrix
    }

}
