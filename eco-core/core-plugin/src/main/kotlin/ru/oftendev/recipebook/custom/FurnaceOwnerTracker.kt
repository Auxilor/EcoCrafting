package ru.oftendev.recipebook.custom

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import java.util.UUID

object FurnaceOwnerTracker : Listener {
    private val owners = mutableMapOf<Location, UUID>()

    private val furnaceTypes = setOf(
        InventoryType.FURNACE,
        InventoryType.BLAST_FURNACE,
        InventoryType.SMOKER
    )

    @EventHandler
    fun onOpen(event: InventoryOpenEvent) {
        if (event.inventory.type !in furnaceTypes) return
        val loc = event.inventory.location ?: return
        owners[loc] = event.player.uniqueId
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        if (event.inventory.type !in furnaceTypes) return
        val loc = event.inventory.location ?: return
        owners.remove(loc)
    }

    fun getOwner(location: Location): Player? {
        val uuid = owners[location] ?: return null
        return location.world?.players?.firstOrNull { it.uniqueId == uuid }
    }
}
