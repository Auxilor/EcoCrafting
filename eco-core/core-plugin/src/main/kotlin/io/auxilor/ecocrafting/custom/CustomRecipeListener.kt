package io.auxilor.ecocrafting.custom

import com.willfp.eco.core.recipe.Recipes
import com.willfp.eco.core.recipe.workstation.AnvilRecipe
import com.willfp.eco.core.recipe.workstation.BrewingRecipe
import com.willfp.eco.core.recipe.workstation.CrafterRecipe
import com.willfp.eco.core.recipe.workstation.GrindstoneRecipe
import com.willfp.eco.core.recipe.workstation.SmeltingRecipe
import com.willfp.eco.core.recipe.workstation.SmeltingType
import com.willfp.eco.core.recipe.workstation.SmithingRecipe
import com.willfp.eco.core.recipe.workstation.StonecuttingRecipe
import com.willfp.eco.core.recipe.workstation.VillagerRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.willfp.eco.util.formatEco
import io.auxilor.ecocrafting.custom.event.CustomBrewEvent
import io.auxilor.ecocrafting.custom.event.CustomCraftEvent
import io.auxilor.ecocrafting.custom.event.CustomSmeltEvent
import io.auxilor.ecocrafting.custom.event.CustomSmithEvent
import io.auxilor.ecocrafting.custom.event.CustomWorkbenchCraftEvent
import io.auxilor.ecocrafting.plugin
import org.bukkit.Bukkit
import org.bukkit.Keyed
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.block.BrewingStand
import org.bukkit.block.Campfire
import org.bukkit.block.Crafter
import org.bukkit.block.Furnace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockCookEvent
import org.bukkit.event.block.CrafterCraftEvent
import org.bukkit.event.inventory.BrewEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.event.inventory.PrepareGrindstoneEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.MerchantInventory
import org.bukkit.inventory.SmithingInventory
import org.bukkit.inventory.StonecutterInventory
import org.bukkit.persistence.PersistentDataType

internal fun maxCraftsFromGrid(matrix: Array<out ItemStack?>): Int {
    val present = matrix.filter { it != null && !it.type.isAir }
    if (present.isEmpty()) return Int.MAX_VALUE
    return present.minOf { it!!.amount }
}

internal fun maxCraftsFromInput(inputStack: ItemStack?): Int =
    inputStack?.amount ?: 0

class CustomRecipeListener : Listener {

    init {
        WorkstationRecipes.registerBrewCompletedHook { location, recipe, matchedSlots ->
            handleBrewCompleted(location, recipe, matchedSlots)
        }
    }

    // Crafting table + smithing table + stonecutter
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPrepareCraft(event: PrepareItemCraftEvent) {
        val recipe = findCraftingTableRecipe(event.inventory.matrix) ?: return
        val output = recipe.output?.clone() ?: return
        val player = event.view.player as? Player

        val meta = CustomRecipes.getMeta(recipe.key)
        if (player != null && meta != null && meta.showWhenLocked && RecipeUnlockStore.isLocked(player, recipe.key, meta)) {
            output.itemMeta = output.itemMeta?.apply {
                lore = (lore ?: mutableListOf()).apply { addAll(meta.lockedLore) }
            }
        }

        event.inventory.result = output
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onCraft(event: CraftItemEvent) {
        val player = event.whoClicked as? Player ?: return
        val recipeKey = (event.recipe as? Keyed)?.key ?: return

        if (event.view.topInventory is StonecutterInventory) {
            handleStonecutter(event, player, recipeKey)
            return
        }

        if (event.view.topInventory is SmithingInventory) {
            handleSmithing(event, player, recipeKey)
            return
        }

        val baseKey = CustomRecipes.baseKeyForVariant(recipeKey)
        val directMatch = WorkstationRecipes.getByKey(baseKey) as? CrafterRecipe
        // Fall back to matrix match when vanilla recipe fires first (same shape/material)
        val recipe = directMatch ?: findCraftingTableRecipe(event.inventory.matrix) ?: return
        val needsTakeover = directMatch == null
        val meta = CustomRecipes.getMeta(recipe.key) ?: return
        if (!checkCraftingConditions(player, recipe, meta)) {
            event.isCancelled = true
            if (meta.showWhenLocked && RecipeUnlockStore.isLocked(player, recipe.key, meta)) {
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

        val amount = calculateCraftAmount(event, maxCraftsFromGrid(event.inventory.matrix))
        val item = recipe.output?.clone()?.apply { this.amount = amount } ?: return

        val customEvent = CustomCraftEvent(player, recipe, item, amount)
        Bukkit.getPluginManager().callEvent(customEvent)
        if (customEvent.isCancelled) { event.isCancelled = true; return }

        val tookOver = needsTakeover && meta.giveResultItem
        when {
            !meta.giveResultItem -> {
                event.isCancelled = true
                consumeCraftingGrid(event, amount)
            }
            tookOver -> {
                Recipes.takeOverCraftItem(event, item.clone().apply { this.amount = 1 })
            }
        }
        if (tookOver) {
            fireCraftEffects(player, recipe, meta, item.clone().apply { this.amount = 1 }, 1)
        } else {
            fireCraftEffects(player, recipe, meta, item, amount)
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
                plugin.debug("[Smithing] no recipe for key=$recipeKey")
                return
            }

        val meta = CustomRecipes.getMeta(recipe.key) ?: return
        plugin.debug("[Smithing] handleSmithing: key=${recipe.key} giveResultItem=${meta.giveResultItem}")

        if (!checkCraftingConditions(player, recipe, meta)) { event.isCancelled = true; return }

        val item = recipe.output?.clone() ?: return
        val customEvent = CustomSmithEvent(player, recipe, item)
        Bukkit.getPluginManager().callEvent(customEvent)
        if (customEvent.isCancelled) { event.isCancelled = true; return }

        if (!meta.giveResultItem) {
            event.isCancelled = true
            consumeSmithingSlots(event.view.topInventory)
            plugin.server.scheduler.runTask(plugin, Runnable { player.updateInventory() })
        }
        fireCraftEffects(player, recipe, meta, item, 1)
        plugin.debug("[Smithing] effects fired for recipe=${recipe.key}")
    }

    private fun handleStonecutter(event: CraftItemEvent, player: Player, recipeKey: NamespacedKey) {
        val recipe = WorkstationRecipes.getByKey(recipeKey) as? StonecuttingRecipe
            ?: run {
                plugin.debug("[Stonecutter] no recipe for key=$recipeKey")
                return
            }

        val meta = CustomRecipes.getMeta(recipeKey) ?: return

        plugin.debug("[Stonecutter] handleStonecutter: key=$recipeKey giveResultItem=${meta.giveResultItem}")

        if (!checkCraftingConditions(player, recipe, meta)) { event.isCancelled = true; return }

        val amount = calculateCraftAmount(event, maxCraftsFromInput(event.view.topInventory.getItem(0)))
        val item = recipe.output?.clone()?.apply { this.amount = amount } ?: return
        val customEvent = CustomCraftEvent(player, recipe, item, amount)
        Bukkit.getPluginManager().callEvent(customEvent)
        if (customEvent.isCancelled) { event.isCancelled = true; return }

        if (!meta.giveResultItem) {
            event.isCancelled = true
            consumeStonecutterSlot(event.view.topInventory, amount)
            plugin.server.scheduler.runTask(plugin, Runnable { player.updateInventory() })
        }
        fireCraftEffects(player, recipe, meta, item, amount)
        plugin.debug("[Stonecutter] effects fired for recipe=${recipe.key}")
    }

    // Crafter block
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onCrafterCraft(event: CrafterCraftEvent) {
        val recipeKey = (event.recipe as? Keyed)?.key ?: return
        val baseKey = if (recipeKey.namespace == "ecocrafting" && recipeKey.key.endsWith("_crafter"))
            NamespacedKey("ecocrafting", recipeKey.key.removeSuffix("_crafter"))
        else recipeKey

        val recipe = WorkstationRecipes.getByKey(baseKey) as? CrafterRecipe ?: return
        val meta = CustomRecipes.getMeta(baseKey) ?: return

        // Opt-in: only handle Crafter for recipes that declare support-crafter=true.
        // Without this gate, eco's AutocrafterPatch cancels the event and we'd be
        // silently uncancelling every EcoCrafting recipe.
        if (!meta.supportCrafter) return

        if (!meta.giveResultItem) {
            event.isCancelled = true
            val crafterInv = (event.block.state as? Crafter)?.inventory ?: return
            for (slot in 0 until 9) consume(crafterInv, slot)
            val player = BlockOwnerTracker.getOwner(event.block.location) ?: return
            val item = recipe.output?.clone() ?: return
            fireCraftEffects(player, recipe, meta, item, 1)
            return
        }
        // giveResultItem = true: eco's AutocrafterPatch cancels every eco-namespace
        // CrafterCraftEvent. Uncancel and set result so vanilla delivers + consumes.
        event.isCancelled = false
        val item = recipe.output?.clone() ?: return
        event.result = item
        val player = BlockOwnerTracker.getOwner(event.block.location) ?: return
        fireCraftEffects(player, recipe, meta, item, 1)
    }

    // Furnace + campfire
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onSmelt(event: FurnaceSmeltEvent) {
        val location = event.block.location
        val player = BlockOwnerTracker.getOwner(location)

        if (player == null) {
            val noItemMatch = WorkstationRecipes.getAll(SmeltingRecipe::class.java)
                .firstOrNull { recipe ->
                    recipe.smeltingType != SmeltingType.CAMPFIRE && recipe.input.matches(event.source) &&
                    (CustomRecipes.getMeta(recipe.key)?.giveResultItem == false)
                }
            if (noItemMatch != null) event.isCancelled = true
            return
        }

        val recipe = WorkstationRecipes.getAll(SmeltingRecipe::class.java)
            .firstOrNull { it.smeltingType != SmeltingType.CAMPFIRE && it.input.matches(event.source) }
            ?: return
        val meta = CustomRecipes.getMeta(recipe.key) ?: return

        if (!checkCraftingConditions(player, recipe, meta)) { event.isCancelled = true; return }

        val item = recipe.output?.clone() ?: return
        val customEvent = CustomSmeltEvent(player, recipe, item, location)
        Bukkit.getPluginManager().callEvent(customEvent)
        if (customEvent.isCancelled) { event.isCancelled = true; return }

        if (!meta.giveResultItem) {
            event.isCancelled = true
            val furnaceState = event.block.state
            if (furnaceState is Furnace) consume(furnaceState.inventory, 0)
        }
        fireCraftEffects(player, recipe, meta, item, 1)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onCampfire(event: BlockCookEvent) {
        val location = event.block.location
        val player = BlockOwnerTracker.getOwner(location)

        if (player == null) {
            val noItemMatch = WorkstationRecipes.getAll(SmeltingRecipe::class.java)
                .firstOrNull { recipe ->
                    recipe.smeltingType == SmeltingType.CAMPFIRE && recipe.input.matches(event.source) &&
                    (CustomRecipes.getMeta(recipe.key)?.giveResultItem == false)
                }
            if (noItemMatch != null) event.isCancelled = true
            return
        }

        val recipe = WorkstationRecipes.getAll(SmeltingRecipe::class.java)
            .firstOrNull { it.smeltingType == SmeltingType.CAMPFIRE && it.input.matches(event.source) }
            ?: return
        val meta = CustomRecipes.getMeta(recipe.key) ?: return

        if (!checkCraftingConditions(player, recipe, meta)) { event.isCancelled = true; return }

        val item = recipe.output?.clone() ?: return
        val customEvent = CustomSmeltEvent(player, recipe, item, location)
        Bukkit.getPluginManager().callEvent(customEvent)
        if (customEvent.isCancelled) { event.isCancelled = true; return }

        if (!meta.giveResultItem) {
            event.isCancelled = true
            val campfire = event.block.state as? Campfire
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
        }
        fireCraftEffects(player, recipe, meta, item, 1)
    }

    // Brewing stand
    // Eco's BrewingPacketHandler intercepts ingredient placement and fires the brew
    // directly (no BrewEvent). Logic is handled via WorkstationRecipes.brewCompletedHook
    // registered in init. This handler only cancels stale timers if BrewEvent fires anyway.

    @EventHandler(priority = EventPriority.LOWEST)
    fun onBrew(event: BrewEvent) {
        WorkstationRecipes.cancelPendingBrew(event.block.location)
    }

    private fun handleBrewCompleted(location: Location, recipe: BrewingRecipe, matchedSlots: List<Int>) {
        val meta = CustomRecipes.getMeta(recipe.key) ?: return
        val brewer = (location.block.state as? BrewingStand)?.inventory ?: return

        if (!meta.giveResultItem) {
            matchedSlots.forEach { brewer.setItem(it, null) }
        }

        val player = BlockOwnerTracker.getOwner(location) ?: return
        val item = recipe.output?.clone() ?: return

        val ghostPerSlot = plugin.configYml.getBool("brewing-stand.ghost-per-slot")
        if (!meta.giveResultItem && ghostPerSlot) {
            matchedSlots.forEach { _ -> fireCraftEffects(player, recipe, meta, item.clone(), 1) }
        } else {
            fireCraftEffects(player, recipe, meta, item, matchedSlots.size)
        }
    }

    // Grindstone + Anvil prepare (override eco's HIGH firstOrNull)
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPrepareGrindstone(event: PrepareGrindstoneEvent) {
        val inv = event.inventory
        val recipe = WorkstationRecipes.getAll(GrindstoneRecipe::class.java)
            .filter {
                it.item1.matches(inv.getItem(0)) &&
                (it.item2 == null || it.item2!!.matches(inv.getItem(1)))
            }
            // Prefer more specific (two-item) recipes over one-item recipes
            .maxByOrNull { if (it.item2 != null) 1 else 0 }
            ?: return
        CustomRecipes.getMeta(recipe.key) ?: return
        event.result = recipe.output?.clone()
        plugin.server.scheduler.runTask(plugin, Runnable {
            (event.view.player as? Player)?.updateInventory()
        })
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPrepareAnvil(event: PrepareAnvilEvent) {
        val inv = event.inventory
        val recipe = WorkstationRecipes.getAll(AnvilRecipe::class.java)
            .firstOrNull {
                it.base.matches(inv.getItem(0)) &&
                (it.material == null || it.material!!.matches(inv.getItem(1)))
            } ?: return
        CustomRecipes.getMeta(recipe.key) ?: return
        val result = recipe.output?.clone() ?: return
        recipe.resultName?.let { name ->
            val meta = result.itemMeta
            meta?.setDisplayName(name.formatEco())
            result.itemMeta = meta
        }
        event.result = result
        event.inventory.repairCost = recipe.repairCost
    }

    // Smithing ghost result-click
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSmithingResultClick(event: InventoryClickEvent) {
        if (event.inventory.type != InventoryType.SMITHING) return
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
        if (meta.giveResultItem) return

        plugin.debug("[Smithing] onSmithingResultClick: no-item recipe=${recipe.key}")
        if (!checkCraftingConditions(player, recipe, meta)) { event.isCancelled = true; return }

        event.isCancelled = true
        consumeSmithingSlots(inv)
        val item = recipe.output?.clone() ?: return
        val customEvent = CustomSmithEvent(player, recipe, item)
        Bukkit.getPluginManager().callEvent(customEvent)
        if (!customEvent.isCancelled) fireCraftEffects(player, recipe, meta, item, 1)
        plugin.server.scheduler.runTask(plugin, Runnable { player.updateInventory() })
    }

    // Stonecutter ghost result-click
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onStonecutterResultClick(event: InventoryClickEvent) {
        if (event.inventory.type != InventoryType.STONECUTTER) return
        if (event.rawSlot != 1) return
        val player = event.whoClicked as? Player ?: return

        val inv = event.inventory
        val inputItem = inv.getItem(0) ?: return
        val resultItem = inv.getItem(1) ?: return

        val recipe = WorkstationRecipes.getAll(StonecuttingRecipe::class.java)
            .firstOrNull { it.input.matches(inputItem) && it.output?.isSimilar(resultItem) == true } ?: return
        val meta = CustomRecipes.getMeta(recipe.key) ?: return
        if (meta.giveResultItem) return

        plugin.debug("[Stonecutter] onStonecutterResultClick: no-item recipe=${recipe.key}")
        if (!checkCraftingConditions(player, recipe, meta)) { event.isCancelled = true; return }

        val item = resultItem.clone()
        val amount = if (event.isShiftClick) {
            minOf(spaceBasedAmount(player, item), maxCraftsFromInput(inputItem)).coerceAtLeast(1)
        } else 1

        val craftItem = item.clone().apply { this.amount = amount }
        event.isCancelled = true
        consumeStonecutterSlot(inv, amount)
        val customEvent = CustomCraftEvent(player, recipe, craftItem, amount)
        Bukkit.getPluginManager().callEvent(customEvent)
        if (!customEvent.isCancelled) fireCraftEffects(player, recipe, meta, craftItem, amount)
        plugin.server.scheduler.runTask(plugin, Runnable { player.updateInventory() })
    }

    // InventoryClickEvent for grindstone / anvil / villager
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inv = event.inventory

        if (event.rawSlot != 2) return

        val workstationRecipe = when (inv.type) {
            InventoryType.ANVIL ->
                WorkstationRecipes.getAll(AnvilRecipe::class.java)
                    .firstOrNull {
                        it.base.matches(inv.getItem(0)) &&
                        (it.material == null || it.material!!.matches(inv.getItem(1)))
                    } ?: return

            InventoryType.GRINDSTONE ->
                WorkstationRecipes.getAll(GrindstoneRecipe::class.java)
                    .filter {
                        it.item1.matches(inv.getItem(0)) &&
                        (it.item2 == null || it.item2!!.matches(inv.getItem(1)))
                    }
                    .maxByOrNull { if (it.item2 != null) 1 else 0 }
                    ?: return

            InventoryType.MERCHANT -> {
                val merchant = inv as? MerchantInventory ?: return
                val selected = merchant.selectedRecipe ?: return
                val tradeNsKey = NamespacedKey("ecocrafting", "trade_key")
                val tradeKey = selected.result.itemMeta
                    ?.persistentDataContainer
                    ?.get(tradeNsKey, PersistentDataType.STRING)
                WorkstationRecipes.getAll(VillagerRecipe::class.java)
                    .firstOrNull { if (tradeKey != null) it.key.key == tradeKey else selected.result.isSimilar(it.output) }
                    ?: return
            }

            else -> return
        }

        val meta = CustomRecipes.getMeta(workstationRecipe.key) ?: return
        if (!checkCraftingConditions(player, workstationRecipe, meta)) { event.isCancelled = true; return }

        val item = workstationRecipe.output?.clone() ?: return
        val stationType = meta.displayType
        val customEvent = CustomWorkbenchCraftEvent(player, workstationRecipe, item, stationType)

        Bukkit.getPluginManager().callEvent(customEvent)
        if (customEvent.isCancelled) { event.isCancelled = true; return }

        if (!meta.giveResultItem) {
            event.isCancelled = true
            consumeWorkbenchInputs(inv, workstationRecipe)
        }
        fireCraftEffects(player, workstationRecipe, meta, item, 1)
        WorkstationRecipes.clearPendingRecipe(player.uniqueId)
    }

    // Grid consumption helpers
    private fun consume(inv: Inventory, slot: Int, amount: Int = 1) {
        val stack = inv.getItem(slot) ?: return
        val remaining = stack.amount - amount
        if (remaining <= 0) inv.setItem(slot, null)
        else { stack.amount = remaining; inv.setItem(slot, stack) }
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

    private fun consumeSmithingSlots(inv: Inventory) {
        for (slot in 0..2) consume(inv, slot)
    }

    private fun consumeStonecutterSlot(inv: Inventory, amount: Int) {
        consume(inv, 0, amount)
    }

    private fun consumeWorkbenchInputs(inv: Inventory, recipe: WorkstationRecipe) {
        when (recipe) {
            is GrindstoneRecipe -> { consume(inv, 0); if (recipe.item2 != null) consume(inv, 1) }
            is AnvilRecipe      -> { consume(inv, 0); if (recipe.material != null) consume(inv, 1) }
            is VillagerRecipe   -> { consume(inv, 0); if (recipe.input2 != null) consume(inv, 1) }
            else -> {}
        }
    }

    // Key helpers
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
            val idx = required.indexOfFirst { it.matches(slot) }
            if (idx < 0) return false
            required.removeAt(idx)
        }
        return required.isEmpty()
    }

    private fun spaceBasedAmount(player: Player, result: ItemStack): Int {
        val freeSpace = player.inventory.storageContents.sumOf { slot ->
            when {
                slot == null || slot.type.isAir -> result.maxStackSize
                slot.isSimilar(result) -> result.maxStackSize - slot.amount
                else -> 0
            }
        }
        return (freeSpace / result.amount.coerceAtLeast(1)).coerceAtLeast(1)
    }

    private fun calculateCraftAmount(event: CraftItemEvent, ingredientBasedAmount: Int): Int {
        return if (event.isShiftClick) {
            val result = event.recipe.result
            val player = event.whoClicked as Player
            val spaceBased = spaceBasedAmount(player, result)
            val amount = minOf(spaceBased, ingredientBasedAmount).coerceAtLeast(1)
            if (ingredientBasedAmount < spaceBased) {
                plugin.debug("[CraftAmount] capped by ingredients: space=$spaceBased ingredients=$ingredientBasedAmount -> $amount")
            }
            amount
        } else 1
    }
}
