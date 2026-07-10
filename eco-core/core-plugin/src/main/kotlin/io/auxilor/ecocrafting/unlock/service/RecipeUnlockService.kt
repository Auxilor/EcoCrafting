package io.auxilor.ecocrafting.unlock.service

import com.willfp.eco.core.data.profile
import io.auxilor.ecocrafting.api.unlock.UnlockManager
import io.auxilor.ecocrafting.core.persistence.PlayerDataKeys
import io.auxilor.ecocrafting.recipe.model.EcoCraftingMeta
import io.auxilor.ecocrafting.recipe.service.RecipeService
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

class RecipeUnlockService(
    private val dataKeys: PlayerDataKeys,
    private val recipeService: RecipeService
) : UnlockManager {
    fun isUnlocked(player: OfflinePlayer, key: NamespacedKey, meta: EcoCraftingMeta): Boolean {
        val profile = player.profile
        if (key.key in profile.read(dataKeys.lockedRecipeOverrides)) return false
        if (key.key in profile.read(dataKeys.unlockedRecipes)) return true
        return !meta.lockedByDefault
    }

    fun isLocked(player: OfflinePlayer, key: NamespacedKey, meta: EcoCraftingMeta): Boolean =
        !isUnlocked(player, key, meta)

    fun unlock(player: Player, key: NamespacedKey, meta: EcoCraftingMeta) {
        val profile = player.profile
        val locked = profile.read(dataKeys.lockedRecipeOverrides)
        if (key.key in locked) profile.write(dataKeys.lockedRecipeOverrides, locked - key.key)
        val unlocked = profile.read(dataKeys.unlockedRecipes)
        if (key.key !in unlocked) profile.write(dataKeys.unlockedRecipes, unlocked + key.key)
    }

    fun lock(player: Player, key: NamespacedKey, meta: EcoCraftingMeta) {
        val unlocked = player.profile.read(dataKeys.unlockedRecipes)
        if (key.key in unlocked) player.profile.write(dataKeys.unlockedRecipes, unlocked - key.key)
        val locked = player.profile.read(dataKeys.lockedRecipeOverrides)
        if (key.key !in locked) player.profile.write(dataKeys.lockedRecipeOverrides, locked + key.key)
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
