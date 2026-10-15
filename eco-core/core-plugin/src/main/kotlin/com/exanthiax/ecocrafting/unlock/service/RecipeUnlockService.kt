package com.exanthiax.ecocrafting.unlock.service

import com.willfp.eco.core.data.profile
import com.willfp.libreforge.EmptyProvidedHolder
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.TriggerData
import com.exanthiax.ecocrafting.api.unlock.UnlockManager
import com.exanthiax.ecocrafting.core.persistence.PlayerDataKeys
import com.exanthiax.ecocrafting.libreforge.TriggerRecipeUnlocked
import com.exanthiax.ecocrafting.recipe.model.EcoCraftingMeta
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

class RecipeUnlockService(
    private val dataKeys: PlayerDataKeys,
    private val recipeService: RecipeService
) : UnlockManager {
    // Full "namespace:key" string, so two plugins registering the same local id under
    // different namespaces don't collide; `key.key in set` also matches bare-local-id
    // data from older writes.
    private fun matches(stored: List<String>, key: NamespacedKey): Boolean =
        key.toString() in stored || key.key in stored

    fun isManuallyLocked(player: OfflinePlayer, key: NamespacedKey): Boolean {
        val locked = player.profile.read(dataKeys.lockedRecipeOverrides)
        if (matches(locked, key)) return true
        val groupKey = recipeService.getMeta(key)?.groupKey ?: return false
        return matches(locked, groupKey) && !matches(player.profile.read(dataKeys.unlockedRecipes), key)
    }

    // Per-output entries win over the stonecutter group's entry, so legacy `<id>_N` data keeps working.
    fun isUnlocked(player: OfflinePlayer, key: NamespacedKey, meta: EcoCraftingMeta): Boolean {
        val locked = player.profile.read(dataKeys.lockedRecipeOverrides)
        val unlocked = player.profile.read(dataKeys.unlockedRecipes)
        if (matches(locked, key)) return false
        if (matches(unlocked, key)) return true
        meta.groupKey?.let { groupKey ->
            if (matches(locked, groupKey)) return false
            if (matches(unlocked, groupKey)) return true
        }
        return !meta.lockedByDefault
    }

    fun isLocked(player: OfflinePlayer, key: NamespacedKey, meta: EcoCraftingMeta): Boolean =
        !isUnlocked(player, key, meta)

    fun unlock(player: Player, key: NamespacedKey, meta: EcoCraftingMeta) {
        val profile = player.profile
        val locked = profile.read(dataKeys.lockedRecipeOverrides)
        if (matches(locked, key)) profile.write(dataKeys.lockedRecipeOverrides, locked - key.key - key.toString())
        val unlocked = profile.read(dataKeys.unlockedRecipes)
        if (!matches(unlocked, key)) profile.write(dataKeys.unlockedRecipes, unlocked + key.toString())
    }

    fun unlock(player: Player, key: NamespacedKey): Boolean {
        recipeService.groupOutputs(key)?.let { outputKeys ->
            unlockGroup(player, key, outputKeys)
            return true
        }
        unlock(player, key, recipeService.getMeta(key) ?: return false)
        return true
    }

    fun lock(player: Player, key: NamespacedKey): Boolean {
        recipeService.groupOutputs(key)?.let { outputKeys ->
            lockGroup(player, key, outputKeys)
            return true
        }
        lock(player, key, recipeService.getMeta(key) ?: return false)
        return true
    }

    private fun storedForms(keys: List<NamespacedKey>): List<String> =
        keys.flatMap { listOf(it.key, it.toString()) }

    private fun unlockGroup(player: Player, groupKey: NamespacedKey, outputKeys: List<NamespacedKey>) {
        val profile = player.profile
        profile.write(dataKeys.lockedRecipeOverrides, profile.read(dataKeys.lockedRecipeOverrides) - storedForms(outputKeys + groupKey).toSet())
        val unlocked = profile.read(dataKeys.unlockedRecipes)
        if (!matches(unlocked, groupKey)) profile.write(dataKeys.unlockedRecipes, unlocked + groupKey.toString())
    }

    private fun lockGroup(player: Player, groupKey: NamespacedKey, outputKeys: List<NamespacedKey>) {
        val profile = player.profile
        profile.write(dataKeys.unlockedRecipes, profile.read(dataKeys.unlockedRecipes) - storedForms(outputKeys + groupKey).toSet())
        val locked = profile.read(dataKeys.lockedRecipeOverrides)
        if (!matches(locked, groupKey)) profile.write(dataKeys.lockedRecipeOverrides, locked + groupKey.toString())
    }

    fun checkAutoUnlock(player: Player, key: NamespacedKey, meta: EcoCraftingMeta): Boolean {
        if (!meta.lockedByDefault || meta.unlockConditions.isEmpty()) return false
        if (isManuallyLocked(player, key) || !isLocked(player, key, meta)) return false
        if (!meta.unlockConditions.areMet(player.toDispatcher(), EmptyProvidedHolder)) return false
        unlock(player, key, meta)
        TriggerRecipeUnlocked.dispatch(player.toDispatcher(), TriggerData(player = player, text = key.toString()))
        return true
    }

    fun checkAutoUnlocks(player: Player) {
        for (key in recipeService.autoUnlockKeys()) {
            checkAutoUnlock(player, key, recipeService.getMeta(key) ?: continue)
        }
    }

    fun lock(player: Player, key: NamespacedKey, meta: EcoCraftingMeta) {
        val unlocked = player.profile.read(dataKeys.unlockedRecipes)
        if (matches(unlocked, key)) player.profile.write(dataKeys.unlockedRecipes, unlocked - key.key - key.toString())
        val locked = player.profile.read(dataKeys.lockedRecipeOverrides)
        if (!matches(locked, key)) player.profile.write(dataKeys.lockedRecipeOverrides, locked + key.toString())
    }

    // Public-API variants: unlike the internal overloads above, these look up recipe
    // metadata themselves so external consumers don't need an EcoCraftingMeta reference.
    override fun isUnlocked(player: OfflinePlayer, recipeKey: NamespacedKey): Boolean {
        recipeService.groupOutputs(recipeKey)?.let { outputKeys ->
            return outputKeys.all { isUnlocked(player, it) }
        }
        val meta = recipeService.getMeta(recipeKey) ?: return true
        return isUnlocked(player, recipeKey, meta)
    }

    override fun isLocked(player: OfflinePlayer, recipeKey: NamespacedKey): Boolean =
        !isUnlocked(player, recipeKey)
}
