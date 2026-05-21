package ru.oftendev.recipebook

import com.willfp.eco.core.command.impl.PluginCommand
import com.willfp.libreforge.loader.LibreforgePlugin
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import org.bstats.bukkit.Metrics
import org.bstats.charts.SimplePie
import ru.oftendev.recipebook.category.RecipeCategories
import ru.oftendev.recipebook.commands.MainCommand
import ru.oftendev.recipebook.integration.ShopIntegration
import ru.oftendev.recipebook.integration.VaultPackIntegration
import com.willfp.libreforge.conditions.Conditions
import com.willfp.libreforge.effects.Effects
import com.willfp.libreforge.triggers.Triggers
import ru.oftendev.recipebook.custom.BlockOwnerTracker
import ru.oftendev.recipebook.custom.CustomRecipeListener
import ru.oftendev.recipebook.custom.CustomRecipeLoader
import ru.oftendev.recipebook.custom.RecipeUnlockStore
import com.willfp.libreforge.loader.configs.ConfigCategory
import ru.oftendev.recipebook.custom.libreforge.ConditionHasUnlockedRecipe
import ru.oftendev.recipebook.custom.libreforge.EffectLockRecipe
import ru.oftendev.recipebook.custom.libreforge.EffectUnlockRecipe
import ru.oftendev.recipebook.custom.libreforge.TriggerCustomCraft
import ru.oftendev.recipebook.custom.libreforge.TriggerGhostCraft
import ru.oftendev.recipebook.custom.libreforge.TriggerRecipeLocked
import ru.oftendev.recipebook.custom.libreforge.TriggerRecipeUnlocked

lateinit var recipeBookPlugin: RecipeBookPlugin
    private set

private const val BSTATS_PLUGIN_ID = 31426

class RecipeBookPlugin : LibreforgePlugin() {
    private var metrics: Metrics? = null

    init {
        recipeBookPlugin = this
    }

    override fun handleEnable() {
        VaultPackIntegration.init(this)
        ShopIntegration.init(this)
        RecipeCategories.reload()

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
        RecipeCategories.reload()
    }

    override fun loadConfigCategories(): List<ConfigCategory> {
        return listOf(CustomRecipeLoader)
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
            logger.info("[RecipeBook] bStats is bundled but disabled until a real plugin ID is set.")
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
            logger.info("[RecipeBook] bStats metrics enabled for plugin ID $pluginId.")
        }.onFailure {
            logger.warning("[RecipeBook] Failed to initialize bStats: ${it.message}")
        }
    }

    fun debug(message: String) {
        if (configYml.getBool("debug")) {
            logger.info("[debug] $message")
        }
    }
}

fun makeSound(string: String?): Sound? {
    return string?.takeIf { it.isNotBlank() }?.let {
        runCatching { Sound.sound(Key.key(it), Sound.Source.AMBIENT, 1.0f, 1.0f) }.getOrNull()
    }
}

@Deprecated("Use makeSound", ReplaceWith("makeSound(string)"))
fun makesound(string: String?): Sound? = makeSound(string)
