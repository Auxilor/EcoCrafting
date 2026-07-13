package io.auxilor.ecocrafting.crafting.integration

import com.willfp.eco.core.recipe.workstation.AnvilRecipe
import com.willfp.eco.core.recipe.workstation.GrindstoneRecipe
import com.willfp.eco.core.recipe.workstation.VillagerRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.willfp.eco.util.formatEco
import io.auxilor.ecocrafting.EcoCraftingPlugin
import io.auxilor.ecocrafting.crafting.event.CustomWorkbenchCraftEvent
import io.auxilor.ecocrafting.crafting.service.checkCraftingConditions
import io.auxilor.ecocrafting.crafting.service.fireCraftEffects
import io.auxilor.ecocrafting.crafting.service.priceAffordableAmount
import io.auxilor.ecocrafting.recipe.model.requiredAmount
import io.auxilor.ecocrafting.recipe.service.RecipeService
import io.auxilor.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.Bukkit
import org.bukkit.entity.ExperienceOrb
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.event.inventory.PrepareGrindstoneEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.MerchantInventory

// Grindstone, anvil, and villager trades - all three deliver their result via a plain
// InventoryClickEvent on the output slot rather than a dedicated craft event.
class WorkbenchListener(
    private val plugin: EcoCraftingPlugin,
    private val recipeService: RecipeService,
    private val unlockService: RecipeUnlockService
) : Listener {

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
        recipeService.getMeta(recipe.key) ?: return
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
        recipeService.getMeta(recipe.key) ?: return
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

        val meta = recipeService.getMeta(workstationRecipe.key) ?: return
        if (!checkCraftingConditions(plugin, unlockService, player, workstationRecipe, meta)) { event.isCancelled = true; return }

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
        fireCraftEffects(player, workstationRecipe, meta, item, amount, inventory.location?.block)
        WorkstationRecipes.clearPendingRecipe(player.uniqueId)
        // Synchronous, not scheduled: this handler can fire on every click of a rapid/shift-click
        // burst, and queuing a Runnable per click backs up the scheduler under load. Direct
        // inventory.setItem/setItemOnCursor calls above already sync to the client on their own;
        // this is just a safety-net resync and is safe to call inline from an event handler.
        player.updateInventory()
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
}
