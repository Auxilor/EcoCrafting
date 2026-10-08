package com.exanthiax.ecocrafting.limit.service

import com.willfp.eco.core.data.profile
import com.exanthiax.ecocrafting.api.limit.LimitManager
import com.exanthiax.ecocrafting.core.persistence.PlayerDataKeys
import com.exanthiax.ecocrafting.limit.model.ALL_RECIPES
import com.exanthiax.ecocrafting.limit.model.currentPlayerCrafts
import com.exanthiax.ecocrafting.limit.model.nextResetStamp
import com.exanthiax.ecocrafting.limit.model.parsePlayerCraftCounts
import com.exanthiax.ecocrafting.limit.model.parseRecipeValues
import com.exanthiax.ecocrafting.limit.model.recordPlayerCrafts
import com.exanthiax.ecocrafting.limit.model.remainingUnder
import com.exanthiax.ecocrafting.limit.model.serialiseRecipeValues
import com.exanthiax.ecocrafting.recipe.model.EcoCraftingMeta
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

const val LIMIT_BYPASS_PERMISSION = "ecocrafting.limit.bypass"

class CraftLimitService(
    private val dataKeys: PlayerDataKeys,
    private val recipeService: RecipeService
) : LimitManager {
    private val writeLock = Any()

    private fun resets(): Map<String, Long> =
        parseRecipeValues(Bukkit.getServer().profile.read(dataKeys.craftLimitResets))

    private fun globalCounts(): Map<String, Long> =
        parseRecipeValues(Bukkit.getServer().profile.read(dataKeys.craftLimitGlobalCounts))

    fun playerCrafts(player: OfflinePlayer, key: NamespacedKey): Int =
        currentPlayerCrafts(parsePlayerCraftCounts(player.profile.read(dataKeys.craftLimitCounts)), key.toString(), resets())

    fun globalCrafts(key: NamespacedKey): Int = globalCounts()[key.toString()]?.toInt() ?: 0

    private fun playerRemaining(player: OfflinePlayer, key: NamespacedKey, meta: EcoCraftingMeta): Int =
        if (meta.playerCraftLimit < 0) Int.MAX_VALUE else remainingUnder(meta.playerCraftLimit, playerCrafts(player, key))

    private fun globalRemaining(key: NamespacedKey, meta: EcoCraftingMeta): Int =
        if (meta.globalCraftLimit < 0) Int.MAX_VALUE else remainingUnder(meta.globalCraftLimit, globalCrafts(key))

    private fun remaining(player: OfflinePlayer, key: NamespacedKey, meta: EcoCraftingMeta): Int =
        if (!meta.hasCraftLimit) Int.MAX_VALUE else minOf(playerRemaining(player, key, meta), globalRemaining(key, meta))

    fun craftsAllowed(player: Player, key: NamespacedKey, meta: EcoCraftingMeta): Int =
        if (!meta.hasCraftLimit || player.hasPermission(LIMIT_BYPASS_PERMISSION)) Int.MAX_VALUE
        else remaining(player, key, meta)

    fun limitReachedMessage(player: Player, key: NamespacedKey, meta: EcoCraftingMeta, crafts: Int = 1): String? {
        if (!meta.hasCraftLimit || player.hasPermission(LIMIT_BYPASS_PERMISSION)) return null
        return when {
            playerRemaining(player, key, meta) < crafts -> "messages.failed-reason.player-limit-reached"
            globalRemaining(key, meta) < crafts -> "messages.failed-reason.global-limit-reached"
            else -> null
        }
    }

    fun record(player: Player, key: NamespacedKey, meta: EcoCraftingMeta, crafts: Int) {
        if (crafts <= 0 || !meta.hasCraftLimit || player.hasPermission(LIMIT_BYPASS_PERMISSION)) return
        synchronized(writeLock) { recordLocked(player, key, meta, crafts) }
    }

    private fun recordLocked(player: Player, key: NamespacedKey, meta: EcoCraftingMeta, crafts: Int) {
        if (meta.playerCraftLimit >= 0) {
            player.profile.write(
                dataKeys.craftLimitCounts,
                recordPlayerCrafts(
                    parsePlayerCraftCounts(player.profile.read(dataKeys.craftLimitCounts)),
                    key.toString(),
                    crafts,
                    System.currentTimeMillis(),
                    resets()
                ).map { it.serialise() }
            )
        }
        if (meta.globalCraftLimit >= 0) {
            val counts = globalCounts()
            Bukkit.getServer().profile.write(
                dataKeys.craftLimitGlobalCounts,
                (counts + (key.toString() to (counts[key.toString()] ?: 0L) + crafts)).serialiseRecipeValues()
            )
        }
    }

    override fun getPlayerLimit(recipeKey: NamespacedKey): Int? =
        recipeService.getMeta(recipeKey)?.playerCraftLimit?.takeIf { it >= 0 }

    override fun getGlobalLimit(recipeKey: NamespacedKey): Int? =
        recipeService.getMeta(recipeKey)?.globalCraftLimit?.takeIf { it >= 0 }

    override fun getPlayerCrafts(player: OfflinePlayer, recipeKey: NamespacedKey): Int = playerCrafts(player, recipeKey)

    override fun getGlobalCrafts(recipeKey: NamespacedKey): Int = globalCrafts(recipeKey)

    override fun getRemaining(player: OfflinePlayer, recipeKey: NamespacedKey): Int? {
        val meta = recipeService.getMeta(recipeKey) ?: return null
        return remaining(player, recipeKey, meta).takeIf { it != Int.MAX_VALUE }
    }

    override fun resetPlayer(player: OfflinePlayer, recipeKey: NamespacedKey?) = synchronized(writeLock) {
        player.profile.write(
            dataKeys.craftLimitCounts,
            if (recipeKey == null) emptyList()
            else parsePlayerCraftCounts(player.profile.read(dataKeys.craftLimitCounts))
                .filter { it.recipe != recipeKey.toString() }
                .map { it.serialise() }
        )
    }

    override fun resetGlobal(recipeKey: NamespacedKey?) = synchronized(writeLock) {
        Bukkit.getServer().profile.write(
            dataKeys.craftLimitGlobalCounts,
            if (recipeKey == null) emptyList() else (globalCounts() - recipeKey.toString()).serialiseRecipeValues()
        )
    }

    override fun resetAllPlayers(recipeKey: NamespacedKey?) = synchronized(writeLock) {
        val resets = resets()
        val target = recipeKey?.toString() ?: ALL_RECIPES
        Bukkit.getServer().profile.write(
            dataKeys.craftLimitResets,
            (resets + (target to nextResetStamp(System.currentTimeMillis(), resets, target))).serialiseRecipeValues()
        )
    }
}
