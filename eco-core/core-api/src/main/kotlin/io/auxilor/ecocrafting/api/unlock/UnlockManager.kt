package io.auxilor.ecocrafting.api.unlock

import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer

interface UnlockManager {
    fun isUnlocked(player: OfflinePlayer, recipeKey: NamespacedKey): Boolean
    fun isLocked(player: OfflinePlayer, recipeKey: NamespacedKey): Boolean
}
