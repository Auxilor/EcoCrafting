package io.auxilor.ecocrafting.category

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.loader.LibreforgePlugin
import com.willfp.libreforge.loader.configs.RegistrableCategory
import io.auxilor.ecocrafting.plugin
import io.auxilor.ecocrafting.recipe.RecipeResolver
import io.auxilor.ecocrafting.recipe.VanillaRecipeScanner
import org.bukkit.scheduler.BukkitTask

object RecipeCategories : RegistrableCategory<RecipeCategory>("category", "categories") {
    private var pendingPostReload: BukkitTask? = null

    override fun clear(plugin: LibreforgePlugin) {
        registry.clear()
        RecipeResolver.clearCache()
    }

    override fun acceptConfig(plugin: LibreforgePlugin, id: String, config: Config) {
        if (getByID(id) != null) {
            plugin.logger.severe("Duplicate category id '$id' - ignoring the duplicate; category ids must be unique.")
            return
        }
        registry.register(RecipeCategory(id, config))
    }

    override fun afterReload(plugin: LibreforgePlugin) {
        VanillaRecipeScanner.populate(values().toList())
        pendingPostReload?.cancel()
        pendingPostReload = plugin.server.scheduler.runTask(plugin, Runnable {
            pendingPostReload = null
            validateCategories(values().toList())
            RecipeResolver.warmCache(values().flatMap { it.allItemStacks() })
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
