package com.exanthiax.ecocrafting.api.limit

import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer

/**
 * Per-recipe craft limits. A null limit or remaining value means unlimited, and a null
 * recipe key on a reset means every recipe.
 */
interface LimitManager {
    fun getPlayerLimit(recipeKey: NamespacedKey): Int?
    fun getGlobalLimit(recipeKey: NamespacedKey): Int?
    fun getPlayerCrafts(player: OfflinePlayer, recipeKey: NamespacedKey): Int
    fun getGlobalCrafts(recipeKey: NamespacedKey): Int
    fun getRemaining(player: OfflinePlayer, recipeKey: NamespacedKey): Int?
    fun resetPlayer(player: OfflinePlayer, recipeKey: NamespacedKey?)
    fun resetGlobal(recipeKey: NamespacedKey?)
    fun resetAllPlayers(recipeKey: NamespacedKey?)
}
