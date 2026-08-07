package com.exanthiax.ecocrafting

import com.willfp.eco.core.bstats.EcoMetricsChart
import com.willfp.eco.core.command.impl.PluginCommand
import com.willfp.libreforge.conditions.Conditions
import com.willfp.libreforge.effects.Effects
import com.willfp.libreforge.filters.Filters
import com.willfp.libreforge.loader.LibreforgePlugin
import com.willfp.libreforge.loader.configs.ConfigCategory
import com.willfp.libreforge.triggers.Triggers
import com.exanthiax.ecocrafting.api.EcoCraftingApi
import com.exanthiax.ecocrafting.api.EcoCraftingApiImpl
import com.exanthiax.ecocrafting.category.integration.CategoriesManagerImpl
import com.exanthiax.ecocrafting.category.integration.CategoryLoader
import com.exanthiax.ecocrafting.category.service.CategoryService
import com.exanthiax.ecocrafting.commands.CommandCancel
import com.exanthiax.ecocrafting.commands.CommandConfirm
import com.exanthiax.ecocrafting.commands.CommandCreate
import com.exanthiax.ecocrafting.commands.CommandEcoCrafting
import com.exanthiax.ecocrafting.commands.CommandEdit
import com.exanthiax.ecocrafting.commands.CommandLock
import com.exanthiax.ecocrafting.commands.CommandOpen
import com.exanthiax.ecocrafting.commands.CommandReload
import com.exanthiax.ecocrafting.commands.CommandUnlock
import com.exanthiax.ecocrafting.core.persistence.PlayerDataKeys
import com.exanthiax.ecocrafting.crafting.integration.BrewingListener
import com.exanthiax.ecocrafting.crafting.integration.CrafterBlockListener
import com.exanthiax.ecocrafting.crafting.integration.CraftingTableListener
import com.exanthiax.ecocrafting.crafting.integration.SmeltingListener
import com.exanthiax.ecocrafting.crafting.integration.SmithingListener
import com.exanthiax.ecocrafting.crafting.integration.StonecutterListener
import com.exanthiax.ecocrafting.crafting.integration.WorkbenchListener
import com.exanthiax.ecocrafting.libreforge.TriggerCraft
import com.exanthiax.ecocrafting.crafting.service.BlockOwnerService
import com.exanthiax.ecocrafting.recipe.integration.RecipeCapEnforcer
import com.exanthiax.ecocrafting.recipe.integration.RecipeLoader
import com.exanthiax.ecocrafting.recipe.integration.VanillaRecipeScanner
import com.exanthiax.ecocrafting.recipe.service.RecipeResolverService
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.recipegui.service.RecipeGuiServices
import com.exanthiax.ecocrafting.recipegui.ui.RecipeCreatorChatListener
import com.exanthiax.ecocrafting.recipegui.ui.RecipeCreatorGUI
import com.exanthiax.ecocrafting.shop.service.ShopIntegrationService
import com.exanthiax.ecocrafting.unlock.integration.RecipeUnlockJoinListener
import com.exanthiax.ecocrafting.libreforge.ConditionHasUnlockedRecipe
import com.exanthiax.ecocrafting.libreforge.EffectLockRecipe
import com.exanthiax.ecocrafting.libreforge.EffectUnlockRecipe
import com.exanthiax.ecocrafting.libreforge.FilterRecipe
import com.exanthiax.ecocrafting.libreforge.FilterWorkstation
import com.exanthiax.ecocrafting.libreforge.TriggerRecipeLocked
import com.exanthiax.ecocrafting.libreforge.TriggerRecipeUnlocked
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.event.Listener
import org.bukkit.plugin.ServicePriority

class EcoCraftingPlugin : LibreforgePlugin() {
    private val dataKeys = PlayerDataKeys(this)

    private val recipeService = RecipeService(this)
    private val resolverService = RecipeResolverService(this, recipeService)
    private val recipeCapEnforcer = RecipeCapEnforcer()
    private val vanillaRecipeScanner = VanillaRecipeScanner()

    private val categoryLoader = CategoryLoader(resolverService, vanillaRecipeScanner)
    private val categoryService = CategoryService(this, categoryLoader, resolverService)
    private val categoriesManager = CategoriesManagerImpl(categoryLoader)

    private val recipeLoader = RecipeLoader(this, recipeService, recipeCapEnforcer, categoryLoader)

    private val unlockService = RecipeUnlockService(dataKeys, recipeService)
    private val unlockJoinListener = RecipeUnlockJoinListener(recipeService, unlockService)

    // crafting slice - one listener per workstation type instead of one god-listener
    private val blockOwnerService = BlockOwnerService(this)
    private val craftingTableListener = CraftingTableListener(this, recipeService, unlockService)
    private val smithingListener = SmithingListener(this, recipeService, unlockService)
    private val stonecutterListener = StonecutterListener(this, recipeService, unlockService)
    private val crafterBlockListener = CrafterBlockListener(this, recipeService, unlockService, blockOwnerService)
    private val smeltingListener = SmeltingListener(this, recipeService, unlockService, blockOwnerService)
    private val brewingListener = BrewingListener(this, recipeService, unlockService, blockOwnerService)
    private val workbenchListener = WorkbenchListener(this, recipeService, unlockService)

    private val shopIntegrationService = ShopIntegrationService(this)

    private val guiServices = RecipeGuiServices(this, recipeService, resolverService, unlockService, shopIntegrationService)
    private val recipeCreatorGUI = RecipeCreatorGUI(this, guiServices)
    private val recipeCreatorChatListener = RecipeCreatorChatListener(this, recipeCreatorGUI)

    private val apiImpl = EcoCraftingApiImpl(categoriesManager, recipeService, unlockService)

    override fun handleEnable() {
        EffectLockRecipe.recipeService = recipeService
        EffectLockRecipe.unlockService = unlockService
        EffectUnlockRecipe.recipeService = recipeService
        EffectUnlockRecipe.unlockService = unlockService
        ConditionHasUnlockedRecipe.recipeService = recipeService
        ConditionHasUnlockedRecipe.unlockService = unlockService

        Triggers.register(TriggerCraft)
        Triggers.register(TriggerRecipeUnlocked)
        Triggers.register(TriggerRecipeLocked)

        Effects.register(EffectUnlockRecipe)
        Effects.register(EffectLockRecipe)
        Conditions.register(ConditionHasUnlockedRecipe)

        Filters.register(FilterWorkstation)
        Filters.register(FilterRecipe)

        shopIntegrationService.init()

        server.servicesManager.register(
            EcoCraftingApi::class.java,
            apiImpl,
            this,
            ServicePriority.Normal
        )
    }

    override fun handleReload() {
        shopIntegrationService.init()
    }

    override fun handleDisable() {
    }

    override fun loadConfigCategories(): List<ConfigCategory> {
        return listOf(
            categoryLoader,
            recipeLoader
        )
    }

    override fun loadPluginCommands(): List<PluginCommand> {
        return listOf(
            CommandEcoCrafting(
                this,
                categoryLoader,
                categoryService,
                guiServices,
                listOf(
                    CommandReload(this, recipeService),
                    CommandOpen(this, categoryLoader, categoryService, guiServices),
                    CommandCreate(this, recipeCreatorGUI),
                    CommandEdit(this, recipeCreatorGUI),
                    CommandUnlock(this, recipeService, unlockService),
                    CommandLock(this, recipeService, unlockService),
                    CommandConfirm(this, recipeCreatorGUI),
                    CommandCancel(this, recipeCreatorGUI)
                )
            )
        )
    }

    override fun loadListeners(): List<Listener> {
        return listOf(
            blockOwnerService,
            unlockJoinListener,
            craftingTableListener,
            smithingListener,
            stonecutterListener,
            crafterBlockListener,
            smeltingListener,
            brewingListener,
            workbenchListener,
            recipeCreatorChatListener
        )
    }

    override fun getCustomCharts() = listOf(
        EcoMetricsChart.SimplePie("ecoshop_integration_enabled") {
            shopIntegrationService.isEnabled().toString()
        },
        EcoMetricsChart.SimplePie("ecoshop_auto_buy_enabled") {
            shopIntegrationService.isAutoBuyEnabled().toString()
        },
        EcoMetricsChart.SimplePie("plugin_version") {
            if (BuildConfig.FREE_VERSION) "Free" else "Premium"
        },
        EcoMetricsChart.AdvancedPie("recipes_per_workstation") {
            recipeService.allMeta()
                .groupingBy { it.displayType.name }
                .eachCount()
                .ifEmpty { null }
        },
        EcoMetricsChart.SingleLine("total_recipes") {
            recipeService.allMeta().size
        },
        EcoMetricsChart.AdvancedPie("recipes_give_result_item") {
            recipeService.allMeta()
                .groupingBy { it.giveResultItem.toString() }
                .eachCount()
                .ifEmpty { null }
        }
    )

    fun debug(message: String) {
        if (configYml.getBool("debug")) {
            logger.info("[debug] $message")
        }
    }
}
