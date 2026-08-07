package com.exanthiax.ecocrafting.unlock.integration

import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.willfp.libreforge.EmptyProvidedHolder
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.TriggerData
import com.exanthiax.ecocrafting.libreforge.TriggerRecipeUnlocked
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class RecipeUnlockJoinListener(
    private val recipeService: RecipeService,
    private val unlockService: RecipeUnlockService
) : Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        for (recipe in WorkstationRecipes.getAll()) {
            val meta = recipeService.getMeta(recipe.key) ?: continue
            if (!meta.lockedByDefault) continue
            if (!unlockService.isLocked(player, recipe.key, meta)) continue
            if (meta.unlockConditions.areMet(player.toDispatcher(), EmptyProvidedHolder)) {
                unlockService.unlock(player, recipe.key, meta)
                TriggerRecipeUnlocked.dispatch(
                    player.toDispatcher(),
                    TriggerData(player = player, text = recipe.key.toString())
                )
            }
        }
    }
}
