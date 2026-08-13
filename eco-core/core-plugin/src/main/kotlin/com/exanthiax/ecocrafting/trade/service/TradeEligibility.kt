package com.exanthiax.ecocrafting.trade.service

import com.exanthiax.ecocrafting.crafting.service.hasRecipePermission
import com.exanthiax.ecocrafting.recipe.model.EcoCraftingMeta
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import com.willfp.eco.core.recipe.workstation.WorkstationRecipe
import com.willfp.libreforge.EmptyProvidedHolder
import com.willfp.libreforge.toDispatcher
import org.bukkit.entity.Player

// Whether a trade should appear at all in a command-opened merchant.
fun isTradeVisible(
    player: Player,
    recipe: WorkstationRecipe,
    meta: EcoCraftingMeta,
    unlockService: RecipeUnlockService
): Boolean {
    if (!hasRecipePermission(player, recipe)) return false
    if (unlockService.isLocked(player, recipe.key, meta)) return false
    return meta.visibilityConditions.areMet(player.toDispatcher(), EmptyProvidedHolder)
}

// Vanilla needs a finite max-uses per trade; `max-uses: 0` (the default) means "no limit".
// Uses are per-merchant, so they reset every time the command opens a fresh GUI.
fun resolveMaxUses(configured: Int): Int =
    if (configured > 0) configured else Int.MAX_VALUE
