package ru.oftendev.recipebook.category

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.loader.LibreforgePlugin
import com.willfp.libreforge.loader.configs.ConfigCategory
import ru.oftendev.recipebook.recipe.VanillaRecipeScanner
import ru.oftendev.recipebook.validation.CategoryValidator

object CategoryLoader : ConfigCategory("category", "categories") {

    override fun clear(plugin: LibreforgePlugin) {
        RecipeCategories.clear()
    }

    override fun acceptConfig(plugin: LibreforgePlugin, id: String, config: Config) {
        config.set("id", id)
        runCatching { RecipeCategories.register(RecipeCategory(config)) }
            .onFailure { plugin.logger.warning("[RecipeBook] Failed to load category $id: ${it.message}") }
    }

    override fun afterReload(plugin: LibreforgePlugin) {
        CategoryValidator.validate(RecipeCategories.values)
        VanillaRecipeScanner.populate(RecipeCategories.values)
    }
}
