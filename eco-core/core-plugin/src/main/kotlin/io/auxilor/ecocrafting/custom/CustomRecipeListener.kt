package io.auxilor.ecocrafting.custom

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
import io.auxilor.ecocrafting.custom.event.CustomCraftEvent
import io.auxilor.ecocrafting.custom.event.CustomSmeltEvent
import io.auxilor.ecocrafting.custom.event.CustomSmithEvent
import io.auxilor.ecocrafting.custom.event.CustomWorkbenchCraftEvent
import io.auxilor.ecocrafting.plugin
import io.auxilor.ecocrafting.recipe.requiredAmount
import org.bukkit.Bukkit
import org.bukkit.Keyed
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.BrewingStand
import org.bukkit.block.Campfire
import org.bukkit.block.Crafter
import org.bukkit.block.Furnace
import org.bukkit.entity.ExperienceOrb
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
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

internal fun maxCraftsFromGrid(matrix: Array<out ItemStack?>): Int {
    val present = matrix.filter { it != null && !it.type.isAir }
    if (present.isEmpty()) return Int.MAX_VALUE
    return present.minOf { it!!.amount }
}

internal fun maxCraftsFromInput(inputStack: ItemStack?): Int =
    inputStack?.amount ?: 0

object CustomRecipeListener : Listener {

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

        val amount = priceAffordableAmount(player, meta.price, calculateCraftAmount(event, maxCraftsFromGrid(event.inventory.matrix))).coerceAtLeast(1)
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
        fireCraftEffects(player, recipe, meta, item, amount)
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

    private fun handleSmithing(event: CraftItemEvent, player: Player, recipeKey: NamespacedKey) {
        val inventory = event.view.topInventory
        val recipe = (WorkstationRecipes.getByKey(recipeKey)
            ?: WorkstationRecipes.getAll(SmithingRecipe::class.java).firstOrNull {
                it.template.matches(inventory.getItem(0)) &&
                it.base.matches(inventory.getItem(1)) &&
                it.addition.matches(inventory.getItem(2))
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
        meta.price.pay(player, 1.0)
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

        val amount = priceAffordableAmount(player, meta.price, calculateCraftAmount(event, maxCraftsFromInput(event.view.topInventory.getItem(0)))).coerceAtLeast(1)
        val item = recipe.output?.clone()?.apply { this.amount = amount } ?: return
        val customEvent = CustomCraftEvent(player, recipe, item, amount)
        Bukkit.getPluginManager().callEvent(customEvent)
        if (customEvent.isCancelled) { event.isCancelled = true; return }

        meta.price.pay(player, amount.toDouble())
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
            val crafterInventory = (event.block.state as? Crafter)?.inventory ?: return
            for (slot in 0 until 9) consume(crafterInventory, slot)
            val player = BlockOwnerTracker.getOwner(event.block.location) ?: return
            if (!checkCraftingConditions(player, recipe, meta)) return
            meta.price.pay(player, 1.0)
            val item = recipe.output?.clone() ?: return
            fireCraftEffects(player, recipe, meta, item, 1)
            return
        }
        // giveResultItem = true: eco's AutocrafterPatch cancels every eco-namespace
        // CrafterCraftEvent. Uncancel and set result so vanilla delivers + consumes.
        val item = recipe.output?.clone() ?: return
        val player = BlockOwnerTracker.getOwner(event.block.location) ?: return
        if (!checkCraftingConditions(player, recipe, meta)) { event.isCancelled = true; return }
        meta.price.pay(player, 1.0)
        event.isCancelled = false
        event.result = item
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

        // Vanilla always consumes exactly 1 source item per smelt and ignores recipe.input's
        // configured amount. Rather than cancelling the event (which would also skip vanilla's
        // burn-time/cook-time bookkeeping), bypass its output via AIR and write the input/output
        // slots ourselves - cook time itself stays vanilla-driven via the registered recipe.
        event.result = ItemStack(Material.AIR)
        val furnaceState = event.block.state as? Furnace ?: return
        val inventory = furnaceState.inventory

        if (meta.giveResultItem) {
            val existingResult = inventory.result
            inventory.result = if (existingResult == null || existingResult.type.isAir) item
                                else existingResult.apply { amount += item.amount }
        }
        inventory.smelting?.let { source ->
            val remaining = source.amount - recipe.input.requiredAmount()
            inventory.smelting = if (remaining <= 0) null else source.apply { amount = remaining }
        }

        meta.price.pay(player, 1.0)
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
                val requiredAmount = recipe.input.requiredAmount()
                for (slot in 0 until 4) {
                    val slotItem = campfire.getItem(slot) ?: continue
                    if (slotItem.isSimilar(event.source)) {
                        val remaining = slotItem.amount - requiredAmount
                        if (remaining <= 0) campfire.setItem(slot, null)
                        else { slotItem.amount = remaining; campfire.setItem(slot, slotItem) }
                        campfire.update()
                        break
                    }
                }
            }
        }
        meta.price.pay(player, 1.0)
        fireCraftEffects(player, recipe, meta, item, 1)
    }

    // Brewing stand
    // Eco's BrewingPacketHandler intercepts ingredient placement and fires the brew
    // directly (no BrewEvent). Logic is handled via WorkstationRecipes.brewCompletedHook
    // registered in init. This handler only cancels stale timers if BrewEvent fires anyway.

    @EventHandler(priority = EventPriority.LOWEST)
    fun onBrew(event: BrewEvent) {
        WorkstationRecipes.cancelPendingBrew(event.block.location)

        // The brewing stand is still a real vanilla block. If base+ingredient also
        // happen to match a genuine vanilla recipe (e.g. potion + glowstone dust),
        // vanilla brews it on its own timer regardless of our custom recipe - handing
        // the player a real vanilla potion and completely bypassing give-result-item:
        // false / our effects. Cancel vanilla's own completion whenever a custom
        // recipe claims this combo; BrewingPacketHandler's own timer (which fires
        // handleBrewCompleted) is the source of truth for these slots.
        val ingredient = event.contents.ingredient ?: return
        val matchesCustomRecipe = WorkstationRecipes.getAll(BrewingRecipe::class.java).any { recipe ->
            recipe.ingredient.matches(ingredient) &&
                (0..2).any { slot -> recipe.base.matches(event.contents.getItem(slot)) }
        }
        if (matchesCustomRecipe) {
            event.isCancelled = true
        }
    }

    private fun handleBrewCompleted(location: Location, recipe: BrewingRecipe, matchedSlots: List<Int>) {
        val meta = CustomRecipes.getMeta(recipe.key) ?: return
        val brewer = (location.block.state as? BrewingStand)?.inventory ?: return
        val player = BlockOwnerTracker.getOwner(location) ?: return

        if (!checkCraftingConditions(player, recipe, meta)) return

        if (!meta.giveResultItem) {
            matchedSlots.forEach { brewer.setItem(it, null) }
        }

        meta.price.pay(player, matchedSlots.size.toDouble())

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
        val inventory = event.inventory
        val recipe = WorkstationRecipes.getAll(GrindstoneRecipe::class.java)
            .filter {
                it.item1.matches(inventory.getItem(0)) &&
                (it.item2 == null || it.item2!!.matches(inventory.getItem(1)))
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
        val inventory = event.inventory
        val recipe = WorkstationRecipes.getAll(AnvilRecipe::class.java)
            .firstOrNull {
                it.base.matches(inventory.getItem(0)) &&
                (it.material == null || it.material!!.matches(inventory.getItem(1)))
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
        event.inventory.repairCostAmount = recipe.material?.requiredAmount() ?: 1
    }

    // Smithing ghost result-click
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSmithingResultClick(event: InventoryClickEvent) {
        if (event.inventory.type != InventoryType.SMITHING) return
        if (event.rawSlot != 3) return
        val player = event.whoClicked as? Player ?: return

        val inventory = event.inventory
        val recipe = WorkstationRecipes.getAll(SmithingRecipe::class.java)
            .firstOrNull {
                it.template.matches(inventory.getItem(0)) &&
                it.base.matches(inventory.getItem(1)) &&
                it.addition.matches(inventory.getItem(2))
            } ?: return
        val meta = CustomRecipes.getMeta(recipe.key) ?: return
        if (meta.giveResultItem) return

        plugin.debug("[Smithing] onSmithingResultClick: no-item recipe=${recipe.key}")
        if (!checkCraftingConditions(player, recipe, meta)) { event.isCancelled = true; return }

        event.isCancelled = true
        consumeSmithingSlots(inventory)
        meta.price.pay(player, 1.0)
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

        val inventory = event.inventory
        val inputItem = inventory.getItem(0) ?: return
        val resultItem = inventory.getItem(1) ?: return

        val recipe = WorkstationRecipes.getAll(StonecuttingRecipe::class.java)
            .firstOrNull { it.input.matches(inputItem) && it.output?.isSimilar(resultItem) == true } ?: return
        val meta = CustomRecipes.getMeta(recipe.key) ?: return
        if (meta.giveResultItem) return

        plugin.debug("[Stonecutter] onStonecutterResultClick: no-item recipe=${recipe.key}")
        if (!checkCraftingConditions(player, recipe, meta)) { event.isCancelled = true; return }

        val item = resultItem.clone()
        val amount = if (event.isShiftClick) {
            priceAffordableAmount(player, meta.price, minOf(spaceBasedAmount(player, item), maxCraftsFromInput(inputItem)).coerceAtLeast(1))
        } else priceAffordableAmount(player, meta.price, 1)

        val craftItem = item.clone().apply { this.amount = amount }
        event.isCancelled = true
        consumeStonecutterSlot(inventory, amount)
        meta.price.pay(player, amount.toDouble())
        val customEvent = CustomCraftEvent(player, recipe, craftItem, amount)
        Bukkit.getPluginManager().callEvent(customEvent)
        if (!customEvent.isCancelled) fireCraftEffects(player, recipe, meta, craftItem, amount)
        plugin.server.scheduler.runTask(plugin, Runnable { player.updateInventory() })
    }

    // InventoryClickEvent for grindstone / anvil / villager
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inventory = event.inventory

        if (event.rawSlot != 2) return

        val workstationRecipe = when (inventory.type) {
            InventoryType.ANVIL ->
                WorkstationRecipes.getAll(AnvilRecipe::class.java)
                    .firstOrNull {
                        it.base.matches(inventory.getItem(0)) &&
                        (it.material == null || it.material!!.matches(inventory.getItem(1)))
                    } ?: return

            InventoryType.GRINDSTONE ->
                WorkstationRecipes.getAll(GrindstoneRecipe::class.java)
                    .filter {
                        it.item1.matches(inventory.getItem(0)) &&
                        (it.item2 == null || it.item2!!.matches(inventory.getItem(1)))
                    }
                    .maxByOrNull { if (it.item2 != null) 1 else 0 }
                    ?: return

            InventoryType.MERCHANT -> {
                val merchant = inventory as? MerchantInventory ?: return
                val selected = merchant.selectedRecipe ?: return
                WorkstationRecipes.getAll(VillagerRecipe::class.java)
                    .firstOrNull { it.matchesMerchantRecipe(selected) }
                    ?: return
            }

            else -> return
        }

        val meta = CustomRecipes.getMeta(workstationRecipe.key) ?: return
        if (!checkCraftingConditions(player, workstationRecipe, meta)) { event.isCancelled = true; return }

        val output = workstationRecipe.output ?: return

        val amount = if (workstationRecipe is GrindstoneRecipe && event.isShiftClick) {
            val item1Amount = workstationRecipe.item1.requiredAmount()
            val availableFromItem1 = (inventory.getItem(0)?.amount ?: 0) / item1Amount
            val availableFromItem2 = workstationRecipe.item2?.let { item2 ->
                (inventory.getItem(1)?.amount ?: 0) / item2.requiredAmount()
            }
            val ingredientBased = listOfNotNull(availableFromItem1, availableFromItem2).min()
            priceAffordableAmount(player, meta.price, minOf(spaceBasedAmount(player, output), ingredientBased).coerceAtLeast(1))
        } else priceAffordableAmount(player, meta.price, 1)

        val item = output.clone().apply { this.amount = output.amount * amount }
        val stationType = meta.displayType
        val customEvent = CustomWorkbenchCraftEvent(player, workstationRecipe, item, stationType)

        Bukkit.getPluginManager().callEvent(customEvent)
        if (customEvent.isCancelled) { event.isCancelled = true; return }

        val selfHandle = workstationRecipe is GrindstoneRecipe || workstationRecipe is AnvilRecipe || !meta.giveResultItem
        if (selfHandle) {
            event.isCancelled = true
            consumeWorkbenchInputs(inventory, workstationRecipe, amount)
            meta.price.pay(player, amount.toDouble())
            if (inventory.type == InventoryType.MERCHANT) {
                awardVillagerTrade(inventory as MerchantInventory, workstationRecipe as? VillagerRecipe)
            }
            if (meta.giveResultItem) {
                val preferCursor = workstationRecipe is AnvilRecipe && !event.isShiftClick
                giveOrDropItem(player, item.clone(), preferCursor)
            }
        } else {
            meta.price.pay(player, amount.toDouble())
        }
        fireCraftEffects(player, workstationRecipe, meta, item, amount)
        WorkstationRecipes.clearPendingRecipe(player.uniqueId)
        // Synchronous, not scheduled: this handler can fire on every click of a rapid/shift-click
        // burst, and queuing a Runnable per click backs up the scheduler under load. Direct
        // inventory.setItem/setItemOnCursor calls above already sync to the client on their own;
        // this is just a safety-net resync and is safe to call inline from an event handler.
        player.updateInventory()
    }

    // UX difference from vanilla: grindstone/no-item results go straight to the inventory
    // (dropped at the player's feet if full) instead of onto the cursor, since GrindstoneInventory
    // has no consumption-amount API to fix shift-click over-consumption otherwise. Anvil prefers
    // the cursor (matching vanilla) when it's free and the click wasn't a shift-click.
    private fun giveOrDropItem(player: Player, item: ItemStack, preferCursor: Boolean = false) {
        if (preferCursor) {
            val cursor = player.itemOnCursor
            if (cursor.type.isAir) {
                player.setItemOnCursor(item)
                return
            }
            if (cursor.isSimilar(item) && cursor.amount + item.amount <= cursor.maxStackSize) {
                cursor.amount += item.amount
                player.setItemOnCursor(cursor)
                return
            }
        }
        player.inventory.addItem(item).values.forEach { player.world.dropItem(player.location, it) }
    }

    private fun VillagerRecipe.matchesMerchantRecipe(merchantRecipe: org.bukkit.inventory.MerchantRecipe): Boolean {
        val ingredients = merchantRecipe.ingredients
        if (ingredients.isEmpty() || !input1.matches(ingredients[0])) return false
        val secondInput = input2
        return if (secondInput != null) ingredients.size > 1 && secondInput.matches(ingredients[1])
               else ingredients.size <= 1
    }

    private fun awardVillagerTrade(inventory: MerchantInventory, recipe: VillagerRecipe?) {
        val index = inventory.selectedRecipeIndex
        if (index < 0) return
        val merchant = inventory.merchant ?: return

        val merchantRecipe = merchant.getRecipe(index)
        merchantRecipe.uses += 1
        merchant.setRecipe(index, merchantRecipe)

        val xp = recipe?.villagerXp ?: return
        if (xp <= 0) return
        val villager = merchant as? Villager ?: return
        villager.villagerExperience += xp
        villager.world.spawn(villager.location, ExperienceOrb::class.java) {
            it.experience = xp
        }
    }

    // Grid consumption helpers
    private fun consume(inventory: Inventory, slot: Int, amount: Int = 1) {
        val stack = inventory.getItem(slot) ?: return
        val remaining = stack.amount - amount
        if (remaining <= 0) inventory.setItem(slot, null)
        else { stack.amount = remaining; inventory.setItem(slot, stack) }
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

    private fun consumeSmithingSlots(inventory: Inventory) {
        for (slot in 0..2) consume(inventory, slot)
    }

    private fun consumeStonecutterSlot(inventory: Inventory, amount: Int) {
        consume(inventory, 0, amount)
    }

    private fun consumeWorkbenchInputs(inventory: Inventory, recipe: WorkstationRecipe, crafts: Int = 1) {
        when (recipe) {
            is GrindstoneRecipe -> {
                consume(inventory, 0, recipe.item1.requiredAmount() * crafts)
                recipe.item2?.let { consume(inventory, 1, it.requiredAmount() * crafts) }
            }
            is AnvilRecipe -> {
                consume(inventory, 0, recipe.base.requiredAmount() * crafts)
                recipe.material?.let { consume(inventory, 1, it.requiredAmount() * crafts) }
            }
            is VillagerRecipe -> {
                consume(inventory, 0, recipe.input1.requiredAmount() * crafts)
                recipe.input2?.let { consume(inventory, 1, it.requiredAmount() * crafts) }
            }
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
            val index = required.indexOfFirst { it.matches(slot) }
            if (index < 0) return false
            required.removeAt(index)
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
