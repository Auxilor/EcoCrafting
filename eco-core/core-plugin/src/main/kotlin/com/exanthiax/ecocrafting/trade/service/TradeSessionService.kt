package com.exanthiax.ecocrafting.trade.service

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// Tracks which recipe keys back the trades of a command-opened merchant, in merchant order.
// Without this, a merchant click can only be resolved by matching ingredients, which picks the
// wrong recipe (and so the wrong price/effects) whenever two villager recipes share their inputs.
class TradeSessionService : Listener {
    private val sessions = ConcurrentHashMap<UUID, List<NamespacedKey>>()

    fun open(player: Player, keys: List<NamespacedKey>) {
        sessions[player.uniqueId] = keys.toList()
    }

    fun close(player: Player) {
        sessions.remove(player.uniqueId)
    }

    // Null when the player has no command-opened merchant, or the index is out of range - both
    // mean the caller should fall back to matching by ingredients (i.e. a real villager).
    fun keyAt(player: Player, index: Int): NamespacedKey? =
        sessions[player.uniqueId]?.getOrNull(index)

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        (event.player as? Player)?.let { close(it) }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        close(event.player)
    }
}
