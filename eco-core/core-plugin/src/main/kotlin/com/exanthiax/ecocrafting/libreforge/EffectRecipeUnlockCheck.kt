package com.exanthiax.ecocrafting.libreforge

import com.exanthiax.ecocrafting.unlock.integration.RecipeUnlockHolder
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.entity.Player
import wiki.ecohub.config.Config
import wiki.ecohub.core.registry.NoCompileData
import wiki.ecohub.rules.effect.Effect
import wiki.ecohub.rules.effect.Identifiers
import wiki.ecohub.rules.holder.Dispatcher
import wiki.ecohub.rules.holder.ProvidedHolder
import wiki.ecohub.rules.holder.get

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
