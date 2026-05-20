package ru.oftendev.recipebook.custom

import com.willfp.libreforge.EmptyProvidedHolder
import com.willfp.libreforge.toDispatcher
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
import java.util.UUID

class CustomRecipeListener : Listener {

    private val pendingRecipe = mutableMapOf<UUID, CustomRecipe>()

    // ── Crafting table + smithing table + stonecutter ─────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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
        val recipe = CustomRecipes.getByKey(baseKey) as? CustomRecipe.CraftingTable ?: return
        if (!checkCraftingConditions(player, recipe)) { event.isCancelled = true; return }

        val amount = calculateCraftAmount(event)
        val item = recipe.output.clone().apply { this.amount = amount }

        if (recipe.ghost) {
            event.isCancelled = true
            consumeCraftingGrid(event)
            val ce = CustomCraftEvent(player, recipe, item, amount)
            Bukkit.getPluginManager().callEvent(ce)
            if (!ce.isCancelled) fireGhostEffects(player, recipe, item, amount)
        } else {
            val ce = CustomCraftEvent(player, recipe, item, amount)
            Bukkit.getPluginManager().callEvent(ce)
            if (ce.isCancelled) { event.isCancelled = true; return }
            fireCustomCraftTrigger(player, recipe, item, amount)
        }
    }

    private fun handleSmithing(event: CraftItemEvent, player: Player, recipeKey: NamespacedKey) {
        val recipe = CustomRecipes.getByKey(recipeKey) as? CustomRecipe.Smithing ?: return
        if (!checkCraftingConditions(player, recipe)) { event.isCancelled = true; return }

        val item = recipe.output.clone()
        if (recipe.ghost) {
            event.isCancelled = true
            consumeSmithingSlots(event)
            val ce = CustomSmithEvent(player, recipe, item)
            Bukkit.getPluginManager().callEvent(ce)
            if (!ce.isCancelled) fireGhostEffects(player, recipe, item, 1)
        } else {
            val ce = CustomSmithEvent(player, recipe, item)
            Bukkit.getPluginManager().callEvent(ce)
            if (ce.isCancelled) { event.isCancelled = true; return }
            fireCustomCraftTrigger(player, recipe, item, 1)
        }
    }

    private fun handleStonecutter(event: CraftItemEvent, player: Player, recipeKey: NamespacedKey) {
        val (baseId, idx) = parseStonecutterKey(recipeKey) ?: return
        val recipe = CustomRecipes.getByKey(NamespacedKey("recipebook", baseId)) as? CustomRecipe.Stonecutter ?: return
        val out = recipe.outputs.getOrNull(idx) ?: return
        if (!checkCraftingConditions(player, recipe)) { event.isCancelled = true; return }

        val amount = calculateCraftAmount(event)
        val item = out.item.clone().apply { this.amount = amount }

        if (out.ghost) {
            event.isCancelled = true
            consumeStonecutterSlot(event)
            val ce = CustomCraftEvent(player, recipe, item, amount)
            Bukkit.getPluginManager().callEvent(ce)
            if (!ce.isCancelled) fireStonecutterGhostEffects(player, recipe, out, amount)
        } else {
            val ce = CustomCraftEvent(player, recipe, item, amount)
            Bukkit.getPluginManager().callEvent(ce)
            if (ce.isCancelled) { event.isCancelled = true; return }
            fireCustomCraftTrigger(player, recipe, item, amount)
        }
    }

    // ── Furnace + campfire ────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onSmelt(event: org.bukkit.event.inventory.FurnaceSmeltEvent) {
        val loc = event.block.location
        val player = FurnaceOwnerTracker.getOwner(loc)
        if (player == null) {
            recipeBookPlugin.debug("[RecipeListener] Smelt at $loc — owner offline, skipping custom recipe")
            return
        }

        val recipe = CustomRecipes.all()
            .filterIsInstance<CustomRecipe.Smelting>()
            .firstOrNull { it.input.matches(event.source) && it.stationType != SmeltingType.CAMPFIRE }
            ?: return

        if (!checkCraftingConditions(player, recipe)) { event.isCancelled = true; return }

        val item = recipe.output.clone()
        if (recipe.ghost) {
            event.isCancelled = true
            val ce = CustomSmeltEvent(player, recipe, item, loc)
            Bukkit.getPluginManager().callEvent(ce)
            if (!ce.isCancelled) fireGhostEffects(player, recipe, item, 1)
        } else {
            event.result = item
            val ce = CustomSmeltEvent(player, recipe, item, loc)
            Bukkit.getPluginManager().callEvent(ce)
            fireCustomCraftTrigger(player, recipe, item, 1)
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onCampfire(event: org.bukkit.event.block.BlockCookEvent) {
        val loc = event.block.location
        val player = FurnaceOwnerTracker.getOwner(loc) ?: return

        val recipe = CustomRecipes.all()
            .filterIsInstance<CustomRecipe.Smelting>()
            .firstOrNull { it.input.matches(event.source) && it.stationType == SmeltingType.CAMPFIRE }
            ?: return

        if (!checkCraftingConditions(player, recipe)) { event.isCancelled = true; return }

        val item = recipe.output.clone()
        if (recipe.ghost) {
            event.isCancelled = true
            val ce = CustomSmeltEvent(player, recipe, item, loc)
            Bukkit.getPluginManager().callEvent(ce)
            if (!ce.isCancelled) fireGhostEffects(player, recipe, item, 1)
        } else {
            event.result = item
            val ce = CustomSmeltEvent(player, recipe, item, loc)
            Bukkit.getPluginManager().callEvent(ce)
            fireCustomCraftTrigger(player, recipe, item, 1)
        }
    }

    // ── Brewing stand ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBrew(event: org.bukkit.event.inventory.BrewEvent) {
        val loc = event.block.location
        val player = BrewingOwnerTracker.getOwner(loc)
        if (player == null) {
            recipeBookPlugin.debug("[RecipeListener] Brew at $loc — owner offline")
            return
        }

        val brewer = event.contents
        val ingredientSlot = brewer.ingredient ?: return

        val recipe = CustomRecipes.all()
            .filterIsInstance<CustomRecipe.Brewing>()
            .firstOrNull { it.ingredient.matches(ingredientSlot) }
            ?: return

        val matchedSlots = (0..2).filter { recipe.base.matches(brewer.getItem(it)) }
        if (matchedSlots.isEmpty()) return

        if (!checkCraftingConditions(player, recipe)) { event.isCancelled = true; return }

        event.isCancelled = true

        val ing = ingredientSlot.clone()
        if (ing.amount <= 1) brewer.ingredient = null
        else { ing.amount--; brewer.ingredient = ing }

        val item = recipe.output.clone()
        val ce = CustomBrewEvent(player, recipe, item, loc, matchedSlots.size)
        Bukkit.getPluginManager().callEvent(ce)
        if (ce.isCancelled) return

        if (recipe.ghost) {
            matchedSlots.forEach { brewer.setItem(it, null) }
            fireGhostEffects(player, recipe, item, 1)
        } else {
            matchedSlots.forEach { brewer.setItem(it, item.clone()) }
            fireCustomCraftTrigger(player, recipe, item, matchedSlots.size)
        }
    }

    // ── Group B PrepareEvent handlers ─────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onCartographyItem(event: io.papermc.paper.event.player.CartographyItemEvent) {
        val player = event.whoClicked as? Player ?: return
        val inv = event.inventory
        val recipe = CustomRecipes.all()
            .filterIsInstance<CustomRecipe.Cartography>()
            .firstOrNull { it.map.matches(inv.getItem(0)) && it.addition.matches(inv.getItem(1)) }
            ?: return
        if (!recipe.visibilityConditions.areMet(player.toDispatcher(), EmptyProvidedHolder)) return
        pendingRecipe[player.uniqueId] = recipe
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPrepareGrindstone(event: org.bukkit.event.inventory.PrepareGrindstoneEvent) {
        val player = event.view.player as? Player ?: return
        val inv = event.inventory
        val recipe = CustomRecipes.all()
            .filterIsInstance<CustomRecipe.Grindstone>()
            .firstOrNull {
                it.item1.matches(inv.getItem(0)) &&
                (it.item2 == null || it.item2.matches(inv.getItem(1)))
            } ?: return
        if (!recipe.visibilityConditions.areMet(player.toDispatcher(), EmptyProvidedHolder)) return
        event.result = recipe.output.clone()
        pendingRecipe[player.uniqueId] = recipe
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPrepareAnvil(event: org.bukkit.event.inventory.PrepareAnvilEvent) {
        val player = event.view.player as? Player ?: return
        val inv = event.inventory
        val recipe = CustomRecipes.all()
            .filterIsInstance<CustomRecipe.Anvil>()
            .firstOrNull {
                it.base.matches(inv.getItem(0)) &&
                (it.material == null || it.material.matches(inv.getItem(1)))
            } ?: return
        if (!recipe.visibilityConditions.areMet(player.toDispatcher(), EmptyProvidedHolder)) return
        val result = recipe.output.clone()
        recipe.resultName?.let { name ->
            val meta = result.itemMeta
            meta?.setDisplayName(name)
            result.itemMeta = meta
        }
        event.result = result
        event.inventory.repairCost = recipe.repairCost
        pendingRecipe[player.uniqueId] = recipe
    }

    // ── InventoryClickEvent + villager injection ───────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInventoryClick(event: org.bukkit.event.inventory.InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inv = event.inventory

        val isResultSlot = when (inv.type) {
            org.bukkit.event.inventory.InventoryType.CARTOGRAPHY,
            org.bukkit.event.inventory.InventoryType.GRINDSTONE,
            org.bukkit.event.inventory.InventoryType.ANVIL -> event.rawSlot == 2
            org.bukkit.event.inventory.InventoryType.MERCHANT -> event.rawSlot == 2
            else -> return
        }
        if (!isResultSlot) return

        val recipe = pendingRecipe[player.uniqueId] ?: run {
            if (inv.type == org.bukkit.event.inventory.InventoryType.MERCHANT) {
                val merchant = (inv as? org.bukkit.inventory.MerchantInventory) ?: return
                val selected = merchant.selectedRecipe ?: return
                CustomRecipes.all()
                    .filterIsInstance<CustomRecipe.Villager>()
                    .firstOrNull { selected.result.isSimilar(it.output) }
            } else null
        } ?: return

        if (!checkCraftingConditions(player, recipe)) { event.isCancelled = true; return }

        val item = recipe.output.clone()
        val stationType = recipe.displayType
        val ce = CustomWorkbenchCraftEvent(player, recipe, item, stationType)

        if (recipe.ghost) {
            event.isCancelled = true
            consumeWorkbenchInputs(inv, recipe)
            Bukkit.getPluginManager().callEvent(ce)
            if (!ce.isCancelled) fireGhostEffects(player, recipe, item, 1)
        } else {
            Bukkit.getPluginManager().callEvent(ce)
            if (ce.isCancelled) { event.isCancelled = true; return }
            fireCustomCraftTrigger(player, recipe, item, 1)
        }
        pendingRecipe.remove(player.uniqueId)
    }

    @EventHandler
    fun onVillagerOpen(event: org.bukkit.event.inventory.InventoryOpenEvent) {
        if (event.inventory.type != org.bukkit.event.inventory.InventoryType.MERCHANT) return
        val player = event.player as? Player ?: return
        val merchant = (event.inventory.holder as? org.bukkit.entity.AbstractVillager) ?: return

        val villagerRecipes = CustomRecipes.all()
            .filterIsInstance<CustomRecipe.Villager>()
            .filter { it.visibilityConditions.areMet(player.toDispatcher(), EmptyProvidedHolder) }

        if (villagerRecipes.isEmpty()) return

        val existing = merchant.recipes.toMutableList()
        villagerRecipes.forEach { vr ->
            val mr = org.bukkit.inventory.MerchantRecipe(vr.output.clone(), Int.MAX_VALUE)
            mr.addIngredient(vr.input1.displayItem.clone())
            vr.input2?.let { mr.addIngredient(it.displayItem.clone()) }
            existing.add(mr)
        }
        merchant.recipes = existing
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

    private fun consumeSmithingSlots(event: CraftItemEvent) {
        val inv = event.view.topInventory
        for (slot in 0..2) consume(inv, slot)
    }

    private fun consumeStonecutterSlot(event: CraftItemEvent) {
        consume(event.view.topInventory, 0)
    }

    private fun consumeWorkbenchInputs(inv: org.bukkit.inventory.Inventory, recipe: CustomRecipe) {
        when (recipe) {
            is CustomRecipe.Grindstone -> { consume(inv, 0); if (recipe.item2 != null) consume(inv, 1) }
            is CustomRecipe.Anvil      -> { consume(inv, 0); if (recipe.material != null) consume(inv, 1) }
            is CustomRecipe.Villager   -> { consume(inv, 0); if (recipe.input2 != null) consume(inv, 1) }
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

    private fun parseStonecutterKey(key: NamespacedKey): Pair<String, Int>? {
        if (key.namespace != "recipebook") return null
        val lastUnderscore = key.key.lastIndexOf('_')
        if (lastUnderscore < 0) return null
        val idx = key.key.substring(lastUnderscore + 1).toIntOrNull() ?: return null
        return key.key.substring(0, lastUnderscore) to idx
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
