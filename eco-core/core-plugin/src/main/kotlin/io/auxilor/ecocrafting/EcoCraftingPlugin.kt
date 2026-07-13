package io.auxilor.ecocrafting

import com.willfp.eco.core.bstats.EcoMetricsChart
import com.willfp.eco.core.command.impl.PluginCommand
import com.willfp.libreforge.conditions.Conditions
import com.willfp.libreforge.effects.Effects
import com.willfp.libreforge.filters.Filters
import com.willfp.libreforge.loader.LibreforgePlugin
import com.willfp.libreforge.loader.configs.ConfigCategory
import com.willfp.libreforge.triggers.Triggers
import io.auxilor.ecocrafting.api.EcoCraftingApi
import io.auxilor.ecocrafting.api.EcoCraftingApiImpl
import io.auxilor.ecocrafting.category.integration.CategoriesManagerImpl
import io.auxilor.ecocrafting.category.integration.CategoryLoader
import io.auxilor.ecocrafting.category.service.CategoryService
import io.auxilor.ecocrafting.commands.CommandCancel
import io.auxilor.ecocrafting.commands.CommandConfirm
import io.auxilor.ecocrafting.commands.CommandCreate
import io.auxilor.ecocrafting.commands.CommandEcoCrafting
import io.auxilor.ecocrafting.commands.CommandEdit
import io.auxilor.ecocrafting.commands.CommandLock
import io.auxilor.ecocrafting.commands.CommandOpen
import io.auxilor.ecocrafting.commands.CommandReload
import io.auxilor.ecocrafting.commands.CommandUnlock
import io.auxilor.ecocrafting.core.persistence.PlayerDataKeys
import io.auxilor.ecocrafting.crafting.integration.BrewingListener
import io.auxilor.ecocrafting.crafting.integration.CrafterBlockListener
import io.auxilor.ecocrafting.crafting.integration.CraftingTableListener
import io.auxilor.ecocrafting.crafting.integration.SmeltingListener
import io.auxilor.ecocrafting.crafting.integration.SmithingListener
import io.auxilor.ecocrafting.crafting.integration.StonecutterListener
import io.auxilor.ecocrafting.crafting.integration.WorkbenchListener
import io.auxilor.ecocrafting.libreforge.TriggerCraft
import io.auxilor.ecocrafting.libreforge.TriggerGhostCraft
import io.auxilor.ecocrafting.libreforge.TriggerCustomCraft
import io.auxilor.ecocrafting.crafting.service.BlockOwnerService
import io.auxilor.ecocrafting.recipe.integration.RecipeCapEnforcer
import io.auxilor.ecocrafting.recipe.integration.RecipeLoader
import io.auxilor.ecocrafting.recipe.integration.VanillaRecipeScanner
import io.auxilor.ecocrafting.recipe.service.RecipeResolverService
import io.auxilor.ecocrafting.recipe.service.RecipeService
import io.auxilor.ecocrafting.recipegui.service.RecipeGuiServices
import io.auxilor.ecocrafting.recipegui.ui.RecipeCreatorChatListener
import io.auxilor.ecocrafting.recipegui.ui.RecipeCreatorGUI
import io.auxilor.ecocrafting.shop.service.ShopIntegrationService
import io.auxilor.ecocrafting.unlock.integration.RecipeUnlockJoinListener
import io.auxilor.ecocrafting.libreforge.ConditionHasUnlockedRecipe
import io.auxilor.ecocrafting.libreforge.EffectLockRecipe
import io.auxilor.ecocrafting.libreforge.EffectUnlockRecipe
import io.auxilor.ecocrafting.libreforge.FilterRecipe
import io.auxilor.ecocrafting.libreforge.FilterWorkstation
import io.auxilor.ecocrafting.libreforge.TriggerRecipeLocked
import io.auxilor.ecocrafting.libreforge.TriggerRecipeUnlocked
import io.auxilor.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.event.Listener
import org.bukkit.plugin.ServicePriority

class EcoCraftingPlugin : LibreforgePlugin() {
    // core kernel
    private val dataKeys = PlayerDataKeys(this)

    // recipe slice
    private val recipeService = RecipeService(this)
    private val resolverService = RecipeResolverService(this, recipeService)
    private val recipeCapEnforcer = RecipeCapEnforcer()
    private val vanillaRecipeScanner = VanillaRecipeScanner()

    // category slice
    private val categoryLoader = CategoryLoader(resolverService, vanillaRecipeScanner)
    private val categoryService = CategoryService(this, categoryLoader, resolverService)
    private val categoriesManager = CategoriesManagerImpl(categoryLoader)

    private val recipeLoader = RecipeLoader(this, recipeService, recipeCapEnforcer, categoryLoader)

    // unlock slice
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

    // shop integration
    private val shopIntegrationService = ShopIntegrationService(this)

    // recipe-gui slice
    private val guiServices = RecipeGuiServices(this, recipeService, resolverService, unlockService, shopIntegrationService)
    private val recipeCreatorGUI = RecipeCreatorGUI(this, guiServices)
    private val recipeCreatorChatListener = RecipeCreatorChatListener(this, recipeCreatorGUI)

    // public API
    private val apiImpl = EcoCraftingApiImpl(categoriesManager, recipeService, unlockService)

    override fun handleEnable() {
        EffectLockRecipe.recipeService = recipeService
        EffectLockRecipe.unlockService = unlockService
        EffectUnlockRecipe.recipeService = recipeService
        EffectUnlockRecipe.unlockService = unlockService
        ConditionHasUnlockedRecipe.recipeService = recipeService
        ConditionHasUnlockedRecipe.unlockService = unlockService

        Triggers.register(TriggerGhostCraft)
        Triggers.register(TriggerCraft)
        Triggers.register(TriggerCustomCraft)
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
        }
    )

    fun debug(message: String) {
        if (configYml.getBool("debug")) {
            logger.info("[debug] $message")
        }
    }
}
