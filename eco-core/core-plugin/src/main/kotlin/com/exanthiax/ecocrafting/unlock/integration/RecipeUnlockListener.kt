package com.exanthiax.ecocrafting.unlock.integration

import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerJoinEvent

class RecipeUnlockListener(
    private val unlockService: RecipeUnlockService
) : Listener {
    private val workstationTypes = setOf(
        InventoryType.WORKBENCH,
        InventoryType.CRAFTER,
        InventoryType.FURNACE,
        InventoryType.BLAST_FURNACE,
        InventoryType.SMOKER,
        InventoryType.SMITHING,
        InventoryType.STONECUTTER,
        InventoryType.BREWING,
        InventoryType.GRINDSTONE,
        InventoryType.ANVIL,
        InventoryType.MERCHANT
    )

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        unlockService.checkAutoUnlocks(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorkstationOpen(event: InventoryOpenEvent) {
        if (event.inventory.type !in workstationTypes) return
        unlockService.checkAutoUnlocks(event.player as? Player ?: return)
    }
}
