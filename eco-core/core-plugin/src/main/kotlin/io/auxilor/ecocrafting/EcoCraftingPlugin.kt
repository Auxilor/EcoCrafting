package io.auxilor.ecocrafting

import com.willfp.eco.core.bstats.EcoMetricsChart
import com.willfp.eco.core.command.impl.PluginCommand
import com.willfp.libreforge.conditions.Conditions
import com.willfp.libreforge.effects.Effects
import com.willfp.libreforge.loader.LibreforgePlugin
import com.willfp.libreforge.loader.configs.ConfigCategory
import com.willfp.libreforge.triggers.Triggers
import io.auxilor.ecocrafting.category.CategoryLoader
import io.auxilor.ecocrafting.commands.CommandEcoCrafting
import io.auxilor.ecocrafting.custom.BlockOwnerTracker
import io.auxilor.ecocrafting.custom.CustomRecipeListener
import io.auxilor.ecocrafting.custom.CustomRecipeLoader
import io.auxilor.ecocrafting.custom.RecipeUnlockStore
import io.auxilor.ecocrafting.custom.libreforge.ConditionHasUnlockedRecipe
import io.auxilor.ecocrafting.custom.libreforge.EffectLockRecipe
import io.auxilor.ecocrafting.custom.libreforge.EffectUnlockRecipe
import io.auxilor.ecocrafting.custom.libreforge.TriggerCustomCraft
import io.auxilor.ecocrafting.custom.libreforge.TriggerGhostCraft
import io.auxilor.ecocrafting.custom.libreforge.TriggerRecipeLocked
import io.auxilor.ecocrafting.custom.libreforge.TriggerRecipeUnlocked
import io.auxilor.ecocrafting.gui.RecipeCreatorChatListener
import io.auxilor.ecocrafting.integration.ShopIntegration
import org.bukkit.event.Listener

internal lateinit var plugin: EcoCraftingPlugin
    private set

class EcoCraftingPlugin : LibreforgePlugin() {
    init {
        plugin = this
    }

    override fun handleEnable() {
        ShopIntegration.init(this)

        Triggers.register(TriggerGhostCraft)
        Triggers.register(TriggerCustomCraft)
        Triggers.register(TriggerRecipeUnlocked)
        Triggers.register(TriggerRecipeLocked)

        Effects.register(EffectUnlockRecipe)
        Effects.register(EffectLockRecipe)
        Conditions.register(ConditionHasUnlockedRecipe)
    }

    override fun handleReload() {
        ShopIntegration.init(this)
    }

    override fun handleDisable() {
        RecipeUnlockStore.saveAll()
    }

    override fun loadConfigCategories(): List<ConfigCategory> {
        return listOf(
            CategoryLoader,
            CustomRecipeLoader
        )
    }

    override fun loadPluginCommands(): List<PluginCommand> {
        return listOf(
            CommandEcoCrafting
        )
    }

    override fun loadListeners(): List<Listener> {
        return listOf(
            BlockOwnerTracker,
            RecipeUnlockStore,
            CustomRecipeListener,
            RecipeCreatorChatListener
        )
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

