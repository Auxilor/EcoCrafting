package io.auxilor.ecocrafting.category

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.loader.LibreforgePlugin
import com.willfp.libreforge.loader.configs.ConfigCategory
import io.auxilor.ecocrafting.plugin
import io.auxilor.ecocrafting.recipe.RecipeResolver
import io.auxilor.ecocrafting.recipe.VanillaRecipeScanner
import org.bukkit.scheduler.BukkitTask

object CategoryLoader : ConfigCategory("category", "categories") {
    private var pendingPostReload: BukkitTask? = null

    override fun clear(plugin: LibreforgePlugin) {
        RecipeCategories.clear()
        RecipeResolver.clearCache()
    }

    override fun acceptConfig(plugin: LibreforgePlugin, id: String, config: Config) {
        config.set("id", id)
        RecipeCategories.register(RecipeCategory(config))
    }

    override fun afterReload(plugin: LibreforgePlugin) {
        VanillaRecipeScanner.populate(RecipeCategories.values)
        pendingPostReload?.cancel()
        pendingPostReload = plugin.server.scheduler.runTask(plugin, Runnable {
            pendingPostReload = null
            validateCategories(RecipeCategories.values)
            RecipeResolver.warmCache(RecipeCategories.values.flatMap { it.allItemStacks() })
        })
    }

    private fun validateCategories(categories: List<RecipeCategory>) {
        for (category in categories) {
            for (stack in category.items) {
                if (RecipeResolver.resolve(stack.item.item) == null) {
                    plugin.logger.warning("Invalid item '${stack.item.item.type.name}' in category '${category.id}': no supported recipe")
                }
            }
        }
    }
}
