package io.auxilor.ecocrafting.custom

import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.willfp.libreforge.EmptyProvidedHolder
import com.willfp.libreforge.toDispatcher
import io.auxilor.ecocrafting.plugin
import java.io.File
import java.util.UUID
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

object RecipeUnlockStore : Listener {
    private val cache = mutableMapOf<UUID, MutableSet<String>>()
    private val lockedOverrides = mutableMapOf<UUID, MutableSet<String>>()

    private fun dataFile(uuid: UUID): File {
        val directory = File(plugin.dataFolder, "data/players")
        directory.mkdirs()
        return File(directory, "$uuid.yml")
    }

    fun loadPlayer(uuid: UUID) {
        val config = YamlConfiguration.loadConfiguration(dataFile(uuid))
        cache[uuid] = config.getStringList("unlocked").toMutableSet()
        lockedOverrides[uuid] = config.getStringList("locked").toMutableSet()
    }

    fun savePlayer(uuid: UUID) {
        val file = dataFile(uuid)
        val config = YamlConfiguration()
        config.set("unlocked", cache[uuid]?.toList() ?: emptyList<String>())
        config.set("locked", lockedOverrides[uuid]?.toList() ?: emptyList<String>())
        config.save(file)
    }

    fun saveAll() {
        cache.keys.toList().forEach { savePlayer(it) }
    }

    fun isUnlocked(player: OfflinePlayer, key: NamespacedKey, meta: EcoCraftingMeta): Boolean {
        val uid = player.uniqueId
        if (key.key in (lockedOverrides[uid] ?: emptySet())) return false
        if (key.key in (cache[uid] ?: emptySet())) return true
        return !meta.lockedByDefault
    }

    fun isLocked(player: OfflinePlayer, key: NamespacedKey, meta: EcoCraftingMeta): Boolean =
        !isUnlocked(player, key, meta)

    fun unlock(player: Player, key: NamespacedKey, meta: EcoCraftingMeta) {
        val uid = player.uniqueId
        lockedOverrides[uid]?.remove(key.key)
        val set = cache.getOrPut(uid) { mutableSetOf() }
        if (!set.add(key.key)) return
        savePlayer(uid)
    }

    fun lock(player: Player, key: NamespacedKey, meta: EcoCraftingMeta) {
        val uid = player.uniqueId
        cache[uid]?.remove(key.key)
        val overrides = lockedOverrides.getOrPut(uid) { mutableSetOf() }
        if (!overrides.add(key.key)) return
        savePlayer(uid)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        loadPlayer(event.player.uniqueId)
        val player = event.player
        for (recipe in WorkstationRecipes.getAll()) {
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
        lockedOverrides.remove(event.player.uniqueId)
    }
}
