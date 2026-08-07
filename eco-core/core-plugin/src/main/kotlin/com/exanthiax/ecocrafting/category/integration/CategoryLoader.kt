package com.exanthiax.ecocrafting.category.integration

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.loader.LibreforgePlugin
import com.willfp.libreforge.loader.configs.RegistrableCategory
import com.exanthiax.ecocrafting.category.model.RecipeCategory
import com.exanthiax.ecocrafting.recipe.integration.VanillaRecipeScanner
import com.exanthiax.ecocrafting.recipe.service.RecipeResolverService
import org.bukkit.scheduler.BukkitTask

class CategoryLoader(
    private val resolverService: RecipeResolverService,
    private val vanillaRecipeScanner: VanillaRecipeScanner
) : RegistrableCategory<RecipeCategory>("category", "categories") {
    private var pendingPostReload: BukkitTask? = null

    override fun clear(plugin: LibreforgePlugin) {
        registry.clear()
        resolverService.clearCache()
    }

    override fun acceptConfig(plugin: LibreforgePlugin, id: String, config: Config) {
        if (getByID(id) != null) {
            plugin.logger.severe("Duplicate category id '$id' - ignoring the duplicate; category ids must be unique.")
            return
        }
        registry.register(RecipeCategory(id, config))
    }

    override fun afterReload(plugin: LibreforgePlugin) {
        vanillaRecipeScanner.populate(values().toList())
        pendingPostReload?.cancel()
        pendingPostReload = plugin.server.scheduler.runTask(plugin, Runnable {
            pendingPostReload = null
            validateCategories(plugin, values().toList())
            resolverService.warmCache(values().flatMap { it.allItemStacks() })
        })
    }

    private fun validateCategories(plugin: LibreforgePlugin, categories: List<RecipeCategory>) {
        for (category in categories) {
            for (stack in category.items) {
                if (resolverService.resolve(stack.item.item) == null) {
                    plugin.logger.warning("Invalid item '${stack.item.item.type.name}' in category '${category.id}': no supported recipe")
                }
            }
            for (subId in category.categories) {
                if (getByID(subId) == null) {
                    plugin.logger.warning("Unknown sub-category '$subId' referenced by category '${category.id}'")
                }
            }
        }
    }
}
