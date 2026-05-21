package ru.oftendev.recipebook.custom

import com.willfp.eco.core.recipe.workstation.AnvilRecipe
import com.willfp.eco.core.recipe.workstation.BrewingRecipe
import com.willfp.eco.core.recipe.workstation.CrafterRecipe
import com.willfp.eco.core.recipe.workstation.GrindstoneRecipe
import com.willfp.eco.core.recipe.workstation.SmeltingRecipe
import com.willfp.eco.core.recipe.workstation.SmeltingType
import com.willfp.eco.core.recipe.workstation.SmithingRecipe
import com.willfp.eco.core.recipe.workstation.StonecuttingRecipe
import com.willfp.eco.core.recipe.workstation.VillagerRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.inventory.SmithingInventory
import org.bukkit.inventory.StonecutterInventory
import ru.oftendev.recipebook.custom.event.CustomBrewEvent
import ru.oftendev.recipebook.custom.event.CustomCraftEvent
import ru.oftendev.recipebook.custom.event.CustomSmeltEvent
import ru.oftendev.recipebook.custom.event.CustomSmithEvent
import ru.oftendev.recipebook.custom.event.CustomWorkbenchCraftEvent
import ru.oftendev.recipebook.recipeBookPlugin

class CustomRecipeListener : Listener {

    // ── Crafting table + smithing table + stonecutter ─────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onCraft(event: CraftItemEvent) {
        val player = event.whoClicked as? Player ?: return
        val recipeKey = (event.recipe as? org.bukkit.Keyed)?.key ?: return

        if (event.view.topInventory is StonecutterInventory) {
            handleStonecutter(event, player, recipeKey)
            return
        }

        if (event.view.topInventory is SmithingInventory) {
            handleSmithing(event, player, recipeKey)
            return
        }

        val baseKey = stripSymmetrySuffix(recipeKey)
        val recipe = WorkstationRecipes.getByKey(baseKey) as? CrafterRecipe ?: return
        val meta = CustomRecipes.getMeta(baseKey) ?: return
        if (!checkCraftingConditions(player, recipe, meta)) { event.isCancelled = true; return }

        val amount = calculateCraftAmount(event)
        val item = recipe.output?.clone()?.apply { this.amount = amount } ?: return

        if (meta.ghost) {
            event.isCancelled = true
            consumeCraftingGrid(event)
            val ce = CustomCraftEvent(player, recipe, item, amount)
            Bukkit.getPluginManager().callEvent(ce)
            if (!ce.isCancelled) fireGhostEffects(player, recipe, meta, item, amount)
        } else {
            val ce = CustomCraftEvent(player, recipe, item, amount)
            Bukkit.getPluginManager().callEvent(ce)
            if (ce.isCancelled) { event.isCancelled = true; return }
            fireCustomCraftTrigger(player, recipe, item, amount)
        }
    }

    private fun handleSmithing(event: CraftItemEvent, player: Player, recipeKey: NamespacedKey) {
        val inv = event.view.topInventory
        val recipe = (WorkstationRecipes.getByKey(recipeKey)
            ?: WorkstationRecipes.getAll(SmithingRecipe::class.java).firstOrNull {
                it.template.matches(inv.getItem(0)) &&
                it.base.matches(inv.getItem(1)) &&
                it.addition.matches(inv.getItem(2))
            }) as? SmithingRecipe
            ?: run {
                recipeBookPlugin.debug("[Smithing] no recipe for key=$recipeKey")
                return
            }

        val meta = CustomRecipes.getMeta(recipe.key) ?: return
        recipeBookPlugin.debug("[Smithing] handleSmithing: key=${recipe.key} ghost=${meta.ghost}")

        if (meta.ghost) {
            event.isCancelled = true
            recipeBookPlugin.debug("[Smithing] ghost: cancelling CraftItemEvent (safety)")
            return
        }

        if (!checkCraftingConditions(player, recipe, meta)) { event.isCancelled = true; return }

        val item = recipe.output?.clone() ?: return
        val ce = CustomSmithEvent(player, recipe, item)
        Bukkit.getPluginManager().callEvent(ce)
        if (ce.isCancelled) { event.isCancelled = true; return }
        fireCustomCraftTrigger(player, recipe, item, 1)
        recipeBookPlugin.debug("[Smithing] non-ghost: effects fired for recipe=${recipe.key}")
    }

    private fun handleStonecutter(event: CraftItemEvent, player: Player, recipeKey: NamespacedKey) {
        val recipe = WorkstationRecipes.getByKey(recipeKey) as? StonecuttingRecipe
            ?: run {
                recipeBookPlugin.debug("[Stonecutter] no recipe for key=$recipeKey")
                return
            }

        val meta = CustomRecipes.getMeta(recipeKey) ?: return

        recipeBookPlugin.debug("[Stonecutter] handleStonecutter: key=$recipeKey ghost=${meta.ghost}")
        if (meta.ghost) {
            event.isCancelled = true
            recipeBookPlugin.debug("[Stonecutter] ghost: cancelling CraftItemEvent (safety)")
            return
        }

        if (!checkCraftingConditions(player, recipe, meta)) { event.isCancelled = true; return }

        val amount = calculateCraftAmount(event)
        val item = recipe.output?.clone()?.apply { this.amount = amount } ?: return
        val ce = CustomCraftEvent(player, recipe, item, amount)
        Bukkit.getPluginManager().callEvent(ce)
        if (ce.isCancelled) { event.isCancelled = true; return }
        fireCustomCraftTrigger(player, recipe, item, amount)
        recipeBookPlugin.debug("[Stonecutter] non-ghost: effects fired for recipe=${recipe.key}")
    }

    // ── Crafter block ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onCrafterCraft(event: org.bukkit.event.block.CrafterCraftEvent) {
        val recipeKey = (event.recipe as? org.bukkit.Keyed)?.key ?: return
        val baseKey = if (recipeKey.namespace == "recipebook" && recipeKey.key.endsWith("_crafter"))
            NamespacedKey("recipebook", recipeKey.key.removeSuffix("_crafter"))
        else recipeKey

        val recipe = WorkstationRecipes.getByKey(baseKey) as? CrafterRecipe ?: return
        val meta = CustomRecipes.getMeta(baseKey) ?: return

        if (meta.ghost) {
            event.isCancelled = true
            val crafterInv = (event.block.state as? org.bukkit.block.Crafter)?.inventory ?: return
            for (slot in 0 until 9) consume(crafterInv, slot)
            val player = BlockOwnerTracker.getOwner(event.block.location) ?: return
            val item = recipe.output?.clone() ?: return
            fireGhostEffects(player, recipe, meta, item, 1)
        }
        // non-ghost: vanilla handles item delivery
    }

    // ── Furnace + campfire ────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onSmelt(event: org.bukkit.event.inventory.FurnaceSmeltEvent) {
        val loc = event.block.location
        val player = BlockOwnerTracker.getOwner(loc)

        if (player == null) {
            val ghostMatch = WorkstationRecipes.getAll(SmeltingRecipe::class.java)
                .firstOrNull { r ->
                    r.smeltingType != SmeltingType.CAMPFIRE && r.input.matches(event.source) &&
                    (CustomRecipes.getMeta(r.key)?.ghost == true)
                }
            if (ghostMatch != null) event.isCancelled = true
            return
        }

        val recipe = WorkstationRecipes.getAll(SmeltingRecipe::class.java)
            .firstOrNull { it.smeltingType != SmeltingType.CAMPFIRE && it.input.matches(event.source) }
            ?: return
        val meta = CustomRecipes.getMeta(recipe.key) ?: return

        if (!checkCraftingConditions(player, recipe, meta)) { event.isCancelled = true; return }

        val item = recipe.output?.clone() ?: return
        if (meta.ghost) {
            event.isCancelled = true
            val furnaceState = event.block.state
            if (furnaceState is org.bukkit.block.Furnace) consume(furnaceState.inventory, 0)
            val ce = CustomSmeltEvent(player, recipe, item, loc)
            Bukkit.getPluginManager().callEvent(ce)
            if (!ce.isCancelled) fireGhostEffects(player, recipe, meta, item, 1)
        } else {
            event.result = item
            val ce = CustomSmeltEvent(player, recipe, item, loc)
            Bukkit.getPluginManager().callEvent(ce)
            fireCustomCraftTrigger(player, recipe, item, 1)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onCampfire(event: org.bukkit.event.block.BlockCookEvent) {
        val loc = event.block.location
        val player = BlockOwnerTracker.getOwner(loc)

        if (player == null) {
            val ghostMatch = WorkstationRecipes.getAll(SmeltingRecipe::class.java)
                .firstOrNull { r ->
                    r.smeltingType == SmeltingType.CAMPFIRE && r.input.matches(event.source) &&
                    (CustomRecipes.getMeta(r.key)?.ghost == true)
                }
            if (ghostMatch != null) event.isCancelled = true
            return
        }

        val recipe = WorkstationRecipes.getAll(SmeltingRecipe::class.java)
            .firstOrNull { it.smeltingType == SmeltingType.CAMPFIRE && it.input.matches(event.source) }
            ?: return
        val meta = CustomRecipes.getMeta(recipe.key) ?: return

        if (!checkCraftingConditions(player, recipe, meta)) { event.isCancelled = true; return }

        val item = recipe.output?.clone() ?: return
        if (meta.ghost) {
            event.isCancelled = true
            val campfire = event.block.state as? org.bukkit.block.Campfire
            if (campfire != null) {
                for (slot in 0 until 4) {
                    val slotItem = campfire.getItem(slot) ?: continue
                    if (slotItem.isSimilar(event.source)) {
                        if (slotItem.amount <= 1) campfire.setItem(slot, null)
                        else { slotItem.amount--; campfire.setItem(slot, slotItem) }
                        campfire.update()
                        break
                    }
                }
            }
            val ce = CustomSmeltEvent(player, recipe, item, loc)
            Bukkit.getPluginManager().callEvent(ce)
            if (!ce.isCancelled) fireGhostEffects(player, recipe, meta, item, 1)
        } else {
            event.result = item
            val ce = CustomSmeltEvent(player, recipe, item, loc)
            Bukkit.getPluginManager().callEvent(ce)
            fireCustomCraftTrigger(player, recipe, item, 1)
        }
    }

    // ── Brewing stand ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onBrew(event: org.bukkit.event.inventory.BrewEvent) {
        val loc = event.block.location

        // Cancel any pending custom-brew timer (non-vanilla ingredient path) to prevent double-firing
        WorkstationRecipes.cancelPendingBrew(loc)

        val player = BlockOwnerTracker.getOwner(loc)
        if (player == null) {
            recipeBookPlugin.debug("[RecipeListener] Brew at $loc — owner offline")
            return
        }

        val brewer = event.contents
        val ingredientSlot = brewer.ingredient ?: return

        val recipe = WorkstationRecipes.getAll(BrewingRecipe::class.java)
            .firstOrNull { it.ingredient.matches(ingredientSlot) }
            ?: return
        val meta = CustomRecipes.getMeta(recipe.key) ?: return

        val matchedSlots = (0..2).filter { recipe.base.matches(brewer.getItem(it)) }
        if (matchedSlots.isEmpty()) return

        if (!checkCraftingConditions(player, recipe, meta)) { event.isCancelled = true; return }

        event.isCancelled = true

        val ing = ingredientSlot.clone()
        if (ing.amount <= 1) brewer.ingredient = null
        else { ing.amount--; brewer.ingredient = ing }

        val item = recipe.output?.clone() ?: return
        val ce = CustomBrewEvent(player, recipe, item, loc, matchedSlots.size)
        Bukkit.getPluginManager().callEvent(ce)
        if (ce.isCancelled) return

        val ghostPerSlot = recipeBookPlugin.configYml.getBool("brewing-stand.ghost-per-slot")
        if (meta.ghost) {
            if (ghostPerSlot) {
                matchedSlots.forEach { slot ->
                    brewer.setItem(slot, null)
                    val slotCe = CustomBrewEvent(player, recipe, item.clone(), loc, 1)
                    Bukkit.getPluginManager().callEvent(slotCe)
                    if (!slotCe.isCancelled) fireGhostEffects(player, recipe, meta, item.clone(), 1)
                }
            } else {
                matchedSlots.forEach { brewer.setItem(it, null) }
                fireGhostEffects(player, recipe, meta, item, 1)
            }
        } else {
            matchedSlots.forEach { brewer.setItem(it, item.clone()) }
            fireCustomCraftTrigger(player, recipe, item, matchedSlots.size)
        }
    }

    // ── Smithing ghost result-click ────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSmithingResultClick(event: org.bukkit.event.inventory.InventoryClickEvent) {
        if (event.inventory.type != org.bukkit.event.inventory.InventoryType.SMITHING) return
        if (event.rawSlot != 3) return
        val player = event.whoClicked as? Player ?: return

        val inv = event.inventory
        val recipe = WorkstationRecipes.getAll(SmithingRecipe::class.java)
            .firstOrNull {
                it.template.matches(inv.getItem(0)) &&
                it.base.matches(inv.getItem(1)) &&
                it.addition.matches(inv.getItem(2))
            } ?: return
        val meta = CustomRecipes.getMeta(recipe.key) ?: return
        if (!meta.ghost) return

        recipeBookPlugin.debug("[Smithing] onSmithingResultClick: ghost recipe=${recipe.key}")
        if (!checkCraftingConditions(player, recipe, meta)) { event.isCancelled = true; return }

        event.isCancelled = true
        consumeSmithingSlots(inv)
        val item = recipe.output?.clone() ?: return
        val ce = CustomSmithEvent(player, recipe, item)
        Bukkit.getPluginManager().callEvent(ce)
        if (!ce.isCancelled) fireGhostEffects(player, recipe, meta, item, 1)
        recipeBookPlugin.server.scheduler.runTask(recipeBookPlugin, Runnable { player.updateInventory() })
    }

    // ── Stonecutter ghost result-click ────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onStonecutterResultClick(event: org.bukkit.event.inventory.InventoryClickEvent) {
        if (event.inventory.type != org.bukkit.event.inventory.InventoryType.STONECUTTER) return
        if (event.rawSlot != 1) return
        val player = event.whoClicked as? Player ?: return

        val inv = event.inventory
        val inputItem = inv.getItem(0) ?: return
        val resultItem = inv.getItem(1) ?: return

        val recipe = WorkstationRecipes.getAll(StonecuttingRecipe::class.java)
            .firstOrNull { it.input.matches(inputItem) && it.output?.isSimilar(resultItem) == true } ?: return
        val meta = CustomRecipes.getMeta(recipe.key) ?: return
        if (!meta.ghost) return

        recipeBookPlugin.debug("[Stonecutter] onStonecutterResultClick: ghost recipe=${recipe.key}")
        if (!checkCraftingConditions(player, recipe, meta)) { event.isCancelled = true; return }

        val item = resultItem.clone()
        val amount = if (event.isShiftClick) {
            val playerInv = player.inventory
            val freeSpace = playerInv.storageContents.sumOf { slot ->
                when {
                    slot == null || slot.type.isAir -> item.maxStackSize
                    slot.isSimilar(item) -> item.maxStackSize - slot.amount
                    else -> 0
                }
            }
            (freeSpace / item.amount.coerceAtLeast(1)).coerceAtLeast(1)
        } else 1

        val craftItem = item.clone().apply { this.amount = amount }
        event.isCancelled = true
        consumeStonecutterSlot(inv)
        val ce = CustomCraftEvent(player, recipe, craftItem, amount)
        Bukkit.getPluginManager().callEvent(ce)
        if (!ce.isCancelled) fireGhostEffects(player, recipe, meta, craftItem, amount)
        recipeBookPlugin.server.scheduler.runTask(recipeBookPlugin, Runnable { player.updateInventory() })
    }

    // ── InventoryClickEvent for grindstone / anvil / villager ─────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: org.bukkit.event.inventory.InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inv = event.inventory

        val isResultSlot = when (inv.type) {
            org.bukkit.event.inventory.InventoryType.GRINDSTONE,
            org.bukkit.event.inventory.InventoryType.ANVIL -> event.rawSlot == 2
            org.bukkit.event.inventory.InventoryType.MERCHANT -> event.rawSlot == 2
            else -> return
        }
        if (!isResultSlot) return

        val workstationRecipe = WorkstationRecipes.getPendingRecipe(player.uniqueId) ?: run {
            if (inv.type == org.bukkit.event.inventory.InventoryType.MERCHANT) {
                val merchant = (inv as? org.bukkit.inventory.MerchantInventory) ?: return
                val selected = merchant.selectedRecipe ?: return
                WorkstationRecipes.getAll(VillagerRecipe::class.java)
                    .firstOrNull { selected.result.isSimilar(it.output) }
            } else null
        } ?: return

        val meta = CustomRecipes.getMeta(workstationRecipe.key) ?: return
        if (!checkCraftingConditions(player, workstationRecipe, meta)) { event.isCancelled = true; return }

        val item = workstationRecipe.output?.clone() ?: return
        val stationType = meta.displayType
        val ce = CustomWorkbenchCraftEvent(player, workstationRecipe, item, stationType)

        if (meta.ghost) {
            event.isCancelled = true
            consumeWorkbenchInputs(inv, workstationRecipe)
            Bukkit.getPluginManager().callEvent(ce)
            if (!ce.isCancelled) fireGhostEffects(player, workstationRecipe, meta, item, 1)
        } else {
            Bukkit.getPluginManager().callEvent(ce)
            if (ce.isCancelled) { event.isCancelled = true; return }
            fireCustomCraftTrigger(player, workstationRecipe, item, 1)
        }
        WorkstationRecipes.clearPendingRecipe(player.uniqueId)
    }

    // ── Grid consumption helpers ──────────────────────────────────────────

    private fun consume(inv: org.bukkit.inventory.Inventory, slot: Int) {
        val stack = inv.getItem(slot) ?: return
        if (stack.amount <= 1) inv.setItem(slot, null)
        else { stack.amount--; inv.setItem(slot, stack) }
    }

    private fun consumeCraftingGrid(event: CraftItemEvent) {
        val matrix = event.inventory.matrix
        for (i in matrix.indices) {
            val stack = matrix[i] ?: continue
            if (stack.type.isAir) continue
            if (stack.amount <= 1) matrix[i] = null
            else stack.amount--
        }
        event.inventory.matrix = matrix
    }

    private fun consumeSmithingSlots(inv: org.bukkit.inventory.Inventory) {
        for (slot in 0..2) consume(inv, slot)
    }

    private fun consumeStonecutterSlot(inv: org.bukkit.inventory.Inventory) {
        consume(inv, 0)
    }

    private fun consumeWorkbenchInputs(inv: org.bukkit.inventory.Inventory, recipe: com.willfp.eco.core.recipe.workstation.WorkstationRecipe) {
        when (recipe) {
            is GrindstoneRecipe -> { consume(inv, 0); if (recipe.item2 != null) consume(inv, 1) }
            is AnvilRecipe      -> { consume(inv, 0); if (recipe.material != null) consume(inv, 1) }
            is VillagerRecipe   -> { consume(inv, 0); if (recipe.input2 != null) consume(inv, 1) }
            else -> {}
        }
    }

    // ── Key helpers ───────────────────────────────────────────────────────

    private val symmetrySuffixes = listOf("_rot90", "_rot180", "_rot270", "_mir", "_mir90", "_mir180", "_mir270")

    private fun stripSymmetrySuffix(key: NamespacedKey): NamespacedKey {
        if (key.namespace != "recipebook") return key
        val stripped = symmetrySuffixes.fold(key.key) { acc, suffix -> acc.removeSuffix(suffix) }
        return NamespacedKey("recipebook", stripped)
    }

    private fun calculateCraftAmount(event: CraftItemEvent): Int {
        return if (event.isShiftClick) {
            val result = event.recipe.result
            val playerInv = (event.whoClicked as Player).inventory
            val freeSpace = playerInv.storageContents.sumOf { slot ->
                when {
                    slot == null || slot.type.isAir -> result.maxStackSize
                    slot.isSimilar(result) -> result.maxStackSize - slot.amount
                    else -> 0
                }
            }
            (freeSpace / result.amount).coerceAtLeast(1)
        } else 1
    }
}
