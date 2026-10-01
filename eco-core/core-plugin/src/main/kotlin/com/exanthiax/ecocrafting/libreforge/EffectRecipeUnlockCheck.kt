package com.exanthiax.ecocrafting.libreforge

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.Identifiers
import com.willfp.libreforge.get
import com.exanthiax.ecocrafting.unlock.integration.RecipeUnlockHolder
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.entity.Player

object EffectRecipeUnlockCheck : Effect<NoCompileData>("recipe_unlock_check") {
    lateinit var unlockService: RecipeUnlockService

    override val description = "Internal to EcoCrafting. Unlocks a recipe when its unlock-conditions are met. Not for use in configs."

    override val categories = setOf("crafting")

    override val shouldReload = false

    override fun onEnable(
        dispatcher: Dispatcher<*>,
        config: Config,
        identifiers: Identifiers,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ) {
        val player = dispatcher.get<Player>() ?: return
        val unlockHolder = holder.holder as? RecipeUnlockHolder ?: return
        unlockService.checkAutoUnlock(player, unlockHolder.recipeKey, unlockHolder.meta)
    }
}
