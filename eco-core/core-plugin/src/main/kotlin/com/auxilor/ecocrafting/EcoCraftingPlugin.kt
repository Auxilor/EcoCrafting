package com.auxilor.ecocrafting

import com.willfp.eco.core.command.impl.PluginCommand
import com.willfp.libreforge.loader.LibreforgePlugin
import org.bstats.bukkit.Metrics
import org.bstats.charts.SimplePie
import com.auxilor.ecocrafting.category.CategoryLoader
import com.auxilor.ecocrafting.commands.MainCommand
import com.auxilor.ecocrafting.integration.ShopIntegration
import com.auxilor.ecocrafting.integration.VaultPackIntegration
import com.willfp.libreforge.conditions.Conditions
import com.willfp.libreforge.effects.Effects
import com.willfp.libreforge.triggers.Triggers
import com.auxilor.ecocrafting.custom.BlockOwnerTracker
import com.auxilor.ecocrafting.custom.CustomRecipeListener
import com.auxilor.ecocrafting.custom.CustomRecipeLoader
import com.auxilor.ecocrafting.custom.RecipeUnlockStore
import com.willfp.libreforge.loader.configs.ConfigCategory
import com.auxilor.ecocrafting.custom.libreforge.ConditionHasUnlockedRecipe
import com.auxilor.ecocrafting.custom.libreforge.EffectLockRecipe
import com.auxilor.ecocrafting.custom.libreforge.EffectUnlockRecipe
import com.auxilor.ecocrafting.custom.libreforge.TriggerCustomCraft
import com.auxilor.ecocrafting.custom.libreforge.TriggerGhostCraft
import com.auxilor.ecocrafting.custom.libreforge.TriggerRecipeLocked
import com.auxilor.ecocrafting.custom.libreforge.TriggerRecipeUnlocked

lateinit var EcoCraftingPlugin: EcoCraftingPlugin
    private set

private const val BSTATS_PLUGIN_ID = 31426

class EcoCraftingPlugin : LibreforgePlugin() {
    private var metrics: Metrics? = null

    init {
        EcoCraftingPlugin = this
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

        setupMetrics()
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

    private fun setupMetrics() {
        val pluginId = BSTATS_PLUGIN_ID
        if (pluginId <= 0) {
            logger.info("[EcoCrafting] bStats is bundled but disabled until a real plugin ID is set.")
            return
        }

        runCatching {
            Metrics(this, pluginId).also { metrics ->
                metrics.addCustomChart(
                    SimplePie("ecoshop_integration_enabled") {
                        ShopIntegration.isEnabled().toString()
                    }
                )
                metrics.addCustomChart(
                    SimplePie("ecoshop_auto_buy_enabled") {
                        ShopIntegration.isAutoBuyEnabled().toString()
                    }
                )
                this.metrics = metrics
            }
            logger.info("[EcoCrafting] bStats metrics enabled for plugin ID $pluginId.")
        }.onFailure {
            logger.warning("[EcoCrafting] Failed to initialize bStats: ${it.message}")
        }
    }

    fun debug(message: String) {
        if (configYml.getBool("debug")) {
            logger.info("[debug] $message")
        }
    }
}

