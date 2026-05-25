package com.auxilor.ecocrafting.custom

import com.willfp.libreforge.EmptyProvidedHolder
import com.willfp.libreforge.toDispatcher
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import com.auxilor.ecocrafting.ecoCraftingPlugin
import java.io.File
import java.util.UUID

object RecipeUnlockStore : Listener {
    private val cache = mutableMapOf<UUID, MutableSet<String>>()

    private fun dataFile(uuid: UUID): File {
        val dir = File(ecoCraftingPlugin.dataFolder, "data/players")
        dir.mkdirs()
        return File(dir, "$uuid.yml")
    }

    fun loadPlayer(uuid: UUID) {
        val cfg = YamlConfiguration.loadConfiguration(dataFile(uuid))
        cache[uuid] = cfg.getStringList("unlocked").toMutableSet()
    }

    fun savePlayer(uuid: UUID) {
        val file = dataFile(uuid)
        val cfg = YamlConfiguration()
        cfg.set("unlocked", cache[uuid]?.toList() ?: emptyList<String>())
        cfg.save(file)
    }

    fun saveAll() {
        cache.keys.toList().forEach { savePlayer(it) }
    }

    fun isUnlocked(player: OfflinePlayer, key: NamespacedKey, meta: EcoCraftingMeta): Boolean {
        if (!meta.lockedByDefault) return true
        return key.key in (cache[player.uniqueId] ?: emptySet())
    }

    fun isLocked(player: OfflinePlayer, key: NamespacedKey, meta: EcoCraftingMeta): Boolean =
        !isUnlocked(player, key, meta)

    fun unlock(player: Player, key: NamespacedKey, meta: EcoCraftingMeta) {
        if (!meta.lockedByDefault) return
        val set = cache.getOrPut(player.uniqueId) { mutableSetOf() }
        if (key.key in set) return
        set.add(key.key)
        savePlayer(player.uniqueId)
    }

    fun lock(player: Player, key: NamespacedKey, meta: EcoCraftingMeta) {
        val set = cache[player.uniqueId] ?: return
        if (key.key !in set) return
        set.remove(key.key)
        savePlayer(player.uniqueId)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        loadPlayer(event.player.uniqueId)
        val player = event.player
        for (recipe in com.willfp.eco.core.recipe.workstation.WorkstationRecipes.getAll()) {
            val meta = CustomRecipes.getMeta(recipe.key) ?: continue
            if (!meta.lockedByDefault) continue
            if (!isLocked(player, recipe.key, meta)) continue
            if (meta.unlockConditions.areMet(player.toDispatcher(), EmptyProvidedHolder)) {
                unlock(player, recipe.key, meta)
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        savePlayer(event.player.uniqueId)
        cache.remove(event.player.uniqueId)
    }
}
