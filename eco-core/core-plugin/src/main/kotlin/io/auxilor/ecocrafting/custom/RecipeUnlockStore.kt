package io.auxilor.ecocrafting.custom

import com.willfp.eco.core.data.keys.PersistentDataKey
import com.willfp.eco.core.data.keys.PersistentDataKeyType
import com.willfp.eco.core.data.profile
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.willfp.libreforge.EmptyProvidedHolder
import com.willfp.libreforge.toDispatcher
import io.auxilor.ecocrafting.plugin
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

object RecipeUnlockStore : Listener {
    private val unlockedKey = PersistentDataKey(
        plugin.namespacedKeyFactory.create("unlocked_recipes"),
        PersistentDataKeyType.STRING_LIST,
        emptyList()
    )

    private val lockedKey = PersistentDataKey(
        plugin.namespacedKeyFactory.create("locked_recipe_overrides"),
        PersistentDataKeyType.STRING_LIST,
        emptyList()
    )

    fun isUnlocked(player: OfflinePlayer, key: NamespacedKey, meta: EcoCraftingMeta): Boolean {
        val profile = player.profile
        if (key.key in profile.read(lockedKey)) return false
        if (key.key in profile.read(unlockedKey)) return true
        return !meta.lockedByDefault
    }

    fun isLocked(player: OfflinePlayer, key: NamespacedKey, meta: EcoCraftingMeta): Boolean =
        !isUnlocked(player, key, meta)

    fun unlock(player: Player, key: NamespacedKey, meta: EcoCraftingMeta) {
        val profile = player.profile
        val locked = profile.read(lockedKey)
        if (key.key in locked) profile.write(lockedKey, locked - key.key)
        val unlocked = profile.read(unlockedKey)
        if (key.key !in unlocked) profile.write(unlockedKey, unlocked + key.key)
    }

    fun lock(player: Player, key: NamespacedKey, meta: EcoCraftingMeta) {
        val profile = player.profile
        val unlocked = profile.read(unlockedKey)
        if (key.key in unlocked) profile.write(unlockedKey, unlocked - key.key)
        val locked = profile.read(lockedKey)
        if (key.key !in locked) profile.write(lockedKey, locked + key.key)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
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
}
