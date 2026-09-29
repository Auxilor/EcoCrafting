package com.exanthiax.ecocrafting.unlock.integration

import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.HolderProvider
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.SimpleProvidedHolder
import com.willfp.libreforge.effects.EffectList
import com.willfp.libreforge.get
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

class RecipeUnlockHolderProvider(
    private val plugin: EcoCraftingPlugin,
    private val recipeService: RecipeService,
    private val unlockService: RecipeUnlockService,
    private val unlockEffects: () -> EffectList
) : HolderProvider {
    private val holders = ConcurrentHashMap<NamespacedKey, RecipeUnlockHolder>()

    val isLive: Boolean
        get() = plugin.configYml.getBoolOrNull("unlock-conditions.live") ?: true

    override fun provide(dispatcher: Dispatcher<*>): Collection<ProvidedHolder> {
        if (!isLive) return emptyList()
        val player = dispatcher.get<Player>() ?: return emptyList()
        return recipeService.autoUnlockKeys().mapNotNull { key ->
            val meta = recipeService.getMeta(key) ?: return@mapNotNull null
            if (unlockService.isManuallyLocked(player, key) || !unlockService.isLocked(player, key, meta)) return@mapNotNull null
            val holder = holders[key]?.takeIf { it.meta === meta }
                ?: RecipeUnlockHolder(key, meta, unlockEffects()).also { holders[key] = it }
            SimpleProvidedHolder(holder)
        }
    }
}
