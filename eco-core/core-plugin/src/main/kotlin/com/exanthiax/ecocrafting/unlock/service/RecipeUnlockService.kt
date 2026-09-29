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

    fun isManuallyLocked(player: OfflinePlayer, key: NamespacedKey): Boolean =
        matches(player.profile.read(dataKeys.lockedRecipeOverrides), key)

    fun isUnlocked(player: OfflinePlayer, key: NamespacedKey, meta: EcoCraftingMeta): Boolean {
        if (isManuallyLocked(player, key)) return false
        if (matches(player.profile.read(dataKeys.unlockedRecipes), key)) return true
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
        val meta = recipeService.getMeta(recipeKey) ?: return true
        return isUnlocked(player, recipeKey, meta)
    }

    override fun isLocked(player: OfflinePlayer, recipeKey: NamespacedKey): Boolean =
        !isUnlocked(player, recipeKey)
}
