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
    // Full "namespace:key" string, so two plugins registering the same local id under
    // different namespaces don't collide; `key.key in set` also matches bare-local-id
    // data from older writes.
    private fun matches(stored: List<String>, key: NamespacedKey): Boolean =
        key.toString() in stored || key.key in stored

    fun isUnlocked(player: OfflinePlayer, key: NamespacedKey, meta: EcoCraftingMeta): Boolean {
        val profile = player.profile
        if (matches(profile.read(dataKeys.lockedRecipeOverrides), key)) return false
        if (matches(profile.read(dataKeys.unlockedRecipes), key)) return true
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
