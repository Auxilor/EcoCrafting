package com.auxilor.ecocrafting.category

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.loader.LibreforgePlugin
import com.willfp.libreforge.loader.configs.ConfigCategory
import org.bukkit.scheduler.BukkitTask
import com.auxilor.ecocrafting.recipe.RecipeResolver
import com.auxilor.ecocrafting.recipe.VanillaRecipeScanner
import com.auxilor.ecocrafting.validation.CategoryValidator

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
            CategoryValidator.validate(RecipeCategories.values)
            RecipeResolver.warmCache(RecipeCategories.values.flatMap { it.allItemStacks() })
        })
    }
}
