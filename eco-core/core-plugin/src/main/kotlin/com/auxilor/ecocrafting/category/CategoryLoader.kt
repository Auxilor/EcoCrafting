package com.auxilor.ecocrafting.category

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.loader.LibreforgePlugin
import com.willfp.libreforge.loader.configs.ConfigCategory
import com.auxilor.ecocrafting.recipe.VanillaRecipeScanner
import com.auxilor.ecocrafting.validation.CategoryValidator

object CategoryLoader : ConfigCategory("category", "categories") {

    override fun clear(plugin: LibreforgePlugin) {
        RecipeCategories.clear()
    }

    override fun acceptConfig(plugin: LibreforgePlugin, id: String, config: Config) {
        config.set("id", id)
        runCatching { RecipeCategories.register(RecipeCategory(config)) }
            .onFailure { plugin.logger.warning("[EcoCrafting] Failed to load category $id: ${it.message}") }
    }

    override fun afterReload(plugin: LibreforgePlugin) {
        CategoryValidator.validate(RecipeCategories.values)
        VanillaRecipeScanner.populate(RecipeCategories.values)
    }
}
