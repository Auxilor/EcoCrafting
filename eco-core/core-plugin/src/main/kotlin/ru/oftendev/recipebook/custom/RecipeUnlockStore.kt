package ru.oftendev.recipebook.custom

import com.willfp.libreforge.EmptyProvidedHolder
import com.willfp.libreforge.toDispatcher
import org.bukkit.OfflinePlayer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import ru.oftendev.recipebook.recipeBookPlugin
import java.io.File
import java.util.UUID

object RecipeUnlockStore : Listener {
    private val cache = mutableMapOf<UUID, MutableSet<String>>()

    private fun dataFile(uuid: UUID): File {
        val dir = File(recipeBookPlugin.dataFolder, "data/players")
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

    fun isUnlocked(player: OfflinePlayer, recipe: CustomRecipe): Boolean {
        if (!recipe.lockedByDefault) return true
        return recipe.key.key in (cache[player.uniqueId] ?: emptySet())
    }

    fun isLocked(player: OfflinePlayer, recipe: CustomRecipe): Boolean =
        !isUnlocked(player, recipe)

    fun unlock(player: Player, recipe: CustomRecipe) {
        if (!recipe.lockedByDefault) return
        val set = cache.getOrPut(player.uniqueId) { mutableSetOf() }
        if (recipe.key.key in set) return
        set.add(recipe.key.key)
        savePlayer(player.uniqueId)
    }

    fun lock(player: Player, recipe: CustomRecipe) {
        val set = cache[player.uniqueId] ?: return
        if (recipe.key.key !in set) return
        set.remove(recipe.key.key)
        savePlayer(player.uniqueId)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        loadPlayer(event.player.uniqueId)
        val player = event.player
        for (recipe in CustomRecipes.all()) {
            if (!recipe.lockedByDefault) continue
            if (!isLocked(player, recipe)) continue
            if (recipe.unlockConditions.areMet(player.toDispatcher(), EmptyProvidedHolder)) {
                unlock(player, recipe)
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        savePlayer(event.player.uniqueId)
        cache.remove(event.player.uniqueId)
    }
}
