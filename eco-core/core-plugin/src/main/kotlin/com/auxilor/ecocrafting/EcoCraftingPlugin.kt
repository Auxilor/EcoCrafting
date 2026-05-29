package com.auxilor.ecocrafting

import com.auxilor.ecocrafting.category.CategoryLoader
import com.auxilor.ecocrafting.commands.MainCommand
import com.auxilor.ecocrafting.custom.BlockOwnerTracker
import com.auxilor.ecocrafting.custom.CustomRecipeListener
import com.auxilor.ecocrafting.custom.CustomRecipeLoader
import com.auxilor.ecocrafting.custom.RecipeUnlockStore
import com.auxilor.ecocrafting.custom.libreforge.ConditionHasUnlockedRecipe
import com.auxilor.ecocrafting.custom.libreforge.EffectLockRecipe
import com.auxilor.ecocrafting.custom.libreforge.EffectUnlockRecipe
import com.auxilor.ecocrafting.custom.libreforge.TriggerCustomCraft
import com.auxilor.ecocrafting.custom.libreforge.TriggerGhostCraft
import com.auxilor.ecocrafting.custom.libreforge.TriggerRecipeLocked
import com.auxilor.ecocrafting.custom.libreforge.TriggerRecipeUnlocked
import com.auxilor.ecocrafting.integration.ShopIntegration
import com.auxilor.ecocrafting.integration.VaultPackIntegration
import com.willfp.eco.core.bstats.EcoMetricsChart
import com.willfp.eco.core.command.impl.PluginCommand
import com.willfp.libreforge.conditions.Conditions
import com.willfp.libreforge.effects.Effects
import com.willfp.libreforge.loader.LibreforgePlugin
import com.willfp.libreforge.loader.configs.ConfigCategory
import com.willfp.libreforge.triggers.Triggers

internal lateinit var plugin: EcoCraftingPlugin
    private set

class EcoCraftingPlugin : LibreforgePlugin() {
    init {
        plugin = this
    }

    override fun handleEnable() {
        VaultPackIntegration.init(this)
        ShopIntegration.init(this)

        Triggers.register(TriggerGhostCraft)
        Triggers.register(TriggerCustomCraft)
        Triggers.register(TriggerRecipeUnlocked)
        Triggers.register(TriggerRecipeLocked)

        Effects.register(EffectUnlockRecipe)
        Effects.register(EffectLockRecipe)
        Conditions.register(ConditionHasUnlockedRecipe)

        eventManager.registerListener(BlockOwnerTracker)
        eventManager.registerListener(RecipeUnlockStore)
        eventManager.registerListener(CustomRecipeListener())
    }

    override fun handleReload() {
        ShopIntegration.init(this)
    }

    override fun loadConfigCategories(): List<ConfigCategory> {
        return listOf(CategoryLoader, CustomRecipeLoader)
    }

    override fun handleDisable() {
        RecipeUnlockStore.saveAll()
    }

    override fun loadPluginCommands(): MutableList<PluginCommand> {
        return mutableListOf(MainCommand(this))
    }

    override fun getCustomCharts() = listOf(
        EcoMetricsChart.SimplePie("ecoshop_integration_enabled") {
            ShopIntegration.isEnabled().toString()
        },
        EcoMetricsChart.SimplePie("ecoshop_auto_buy_enabled") {
            ShopIntegration.isAutoBuyEnabled().toString()
        }
    )

    fun debug(message: String) {
        if (configYml.getBool("debug")) {
            logger.info("[debug] $message")
        }
    }
}

