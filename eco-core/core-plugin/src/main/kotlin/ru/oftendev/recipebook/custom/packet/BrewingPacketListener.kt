package ru.oftendev.recipebook.custom.packet

import com.willfp.eco.core.packet.PacketEvent
import com.willfp.eco.core.packet.PacketListener
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.event.inventory.InventoryType
import org.bukkit.scheduler.BukkitTask
import ru.oftendev.recipebook.custom.BlockOwnerTracker
import ru.oftendev.recipebook.custom.CustomRecipe
import ru.oftendev.recipebook.custom.CustomRecipes
import ru.oftendev.recipebook.custom.checkCraftingConditions
import ru.oftendev.recipebook.custom.fireCustomCraftTrigger
import ru.oftendev.recipebook.custom.fireGhostEffects
import ru.oftendev.recipebook.custom.event.CustomBrewEvent
import ru.oftendev.recipebook.recipeBookPlugin

object BrewingPacketListener : PacketListener {

    private val pendingBrews = mutableMapOf<Location, BukkitTask>()

    override fun onReceive(event: PacketEvent) {
        val packet = event.packet.handle
        // Use reflection to avoid depending on NMS at compile time
        if (!packet.javaClass.name.endsWith("ServerboundContainerClickPacket")) return
        val slotNum = runCatching {
            packet.javaClass.getDeclaredField("slotNum").apply { isAccessible = true }.getInt(packet)
        }.getOrElse {
            packet.javaClass.methods.firstOrNull { it.name == "getSlotNum" }?.invoke(packet) as? Int
        } ?: return
        if (slotNum != 3) return

        val player = event.player
        if (player.openInventory.topInventory.type != InventoryType.BREWING) return

        val cursor = player.itemOnCursor
        if (cursor == null || cursor.type.isAir) return

        val recipe = CustomRecipes.all()
            .filterIsInstance<CustomRecipe.Brewing>()
            .firstOrNull { it.ingredient.matches(cursor) } ?: return

        event.isCancelled = true

        Bukkit.getScheduler().runTask(recipeBookPlugin, Runnable {
            val topInv = player.openInventory.topInventory
            if (topInv.type != InventoryType.BREWING) return@Runnable

            val toPlace = cursor.clone().apply { amount = 1 }
            topInv.setItem(3, toPlace)
            if (cursor.amount <= 1) player.setItemOnCursor(null)
            else cursor.amount--
            player.updateInventory()

            // Use block location for stable map key (normalises pitch/yaw to 0)
            val loc = topInv.location?.block?.location ?: return@Runnable
            scheduleBrew(loc, recipe)
        })
    }

    fun cancelBrew(location: Location) {
        pendingBrews.remove(location)?.cancel()
    }

    private fun scheduleBrew(loc: Location, recipe: CustomRecipe.Brewing) {
        pendingBrews[loc]?.cancel()
        pendingBrews[loc] = Bukkit.getScheduler().runTaskLater(recipeBookPlugin, Runnable {
            pendingBrews.remove(loc)

            val state = loc.block.state as? org.bukkit.block.BrewingStand ?: return@Runnable
            val brewer = state.inventory
            val ingredient = brewer.ingredient ?: return@Runnable
            if (!recipe.ingredient.matches(ingredient)) return@Runnable

            val matchedSlots = (0..2).filter { recipe.base.matches(brewer.getItem(it)) }
            if (matchedSlots.isEmpty()) return@Runnable

            val player = BlockOwnerTracker.getOwner(loc) ?: return@Runnable
            if (!checkCraftingConditions(player, recipe)) return@Runnable

            val ing = ingredient.clone()
            if (ing.amount <= 1) brewer.ingredient = null
            else { ing.amount--; brewer.ingredient = ing }

            val item = recipe.output.clone()
            val ce = CustomBrewEvent(player, recipe, item, loc, matchedSlots.size)
            Bukkit.getPluginManager().callEvent(ce)
            if (ce.isCancelled) return@Runnable

            val ghostPerSlot = recipeBookPlugin.configYml.getBool("brewing-stand.ghost-per-slot")
            if (recipe.ghost) {
                if (ghostPerSlot) {
                    matchedSlots.forEach { slot ->
                        brewer.setItem(slot, null)
                        fireGhostEffects(player, recipe, item.clone(), 1)
                    }
                } else {
                    matchedSlots.forEach { brewer.setItem(it, null) }
                    fireGhostEffects(player, recipe, item, 1)
                }
            } else {
                matchedSlots.forEach { brewer.setItem(it, item.clone()) }
                fireCustomCraftTrigger(player, recipe, item, matchedSlots.size)
            }
        }, 400L)
    }
}
