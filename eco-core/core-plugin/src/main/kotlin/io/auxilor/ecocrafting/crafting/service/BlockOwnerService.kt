package io.auxilor.ecocrafting.crafting.service

import io.auxilor.ecocrafting.EcoCraftingPlugin
import java.util.UUID
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.TileState
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.persistence.PersistentDataType

class BlockOwnerService(private val plugin: EcoCraftingPlugin) : Listener {

    private val PDC_KEY = NamespacedKey("ecocrafting", "owner")

    private val TRACKED_MATERIALS = setOf(
        Material.FURNACE,
        Material.BLAST_FURNACE,
        Material.SMOKER,
        Material.BREWING_STAND,
        Material.CRAFTER,
        Material.CAMPFIRE,
        Material.SOUL_CAMPFIRE
    )

    private val TRACKED_INVENTORY_TYPES = setOf(
        InventoryType.FURNACE,
        InventoryType.BLAST_FURNACE,
        InventoryType.SMOKER,
        InventoryType.BREWING,
        InventoryType.CRAFTER
    )

    fun setOwner(block: Block, player: Player) {
        val state = block.state as? TileState ?: return
        state.persistentDataContainer.set(PDC_KEY, PersistentDataType.STRING, player.uniqueId.toString())
        state.update()
    }

    fun hasOwner(block: Block): Boolean {
        val state = block.state as? TileState ?: return false
        return state.persistentDataContainer.has(PDC_KEY, PersistentDataType.STRING)
    }

    private fun getStoredUUID(block: Block): UUID? {
        val state = block.state as? TileState ?: return null
        val rawUuid = state.persistentDataContainer.get(PDC_KEY, PersistentDataType.STRING) ?: return null
        return runCatching { UUID.fromString(rawUuid) }.getOrNull()
    }

    fun getOwner(location: Location): Player? {
        return when (plugin.configYml.getString("owner-mode")) {
            "nearest" -> {
                val radius = plugin.configYml.getInt("owner-nearest-radius")
                    .takeIf { it > 0 } ?: 32
                location.world?.players
                    ?.filter { it.location.distanceSquared(location) <= (radius * radius).toDouble() }
                    ?.minByOrNull { it.location.distanceSquared(location) }
            }
            else -> {
                val uuid = getStoredUUID(location.block) ?: return null
                location.world?.players?.firstOrNull { it.uniqueId == uuid }
            }
        }
    }

    @EventHandler
    fun onPlace(event: BlockPlaceEvent) {
        if (event.block.type !in TRACKED_MATERIALS) return
        setOwner(event.block, event.player)
    }

    @EventHandler
    fun onOpen(event: InventoryOpenEvent) {
        if (event.inventory.type !in TRACKED_INVENTORY_TYPES) return
        val location = event.inventory.location ?: return
        val block = location.block
        if (hasOwner(block)) return
        setOwner(block, event.player as? Player ?: return)
    }

    @EventHandler
    fun onBreak(event: BlockBreakEvent) {
        if (event.block.type !in TRACKED_MATERIALS) return
        val state = event.block.state as? TileState ?: return
        state.persistentDataContainer.remove(PDC_KEY)
        state.update()
    }
}
