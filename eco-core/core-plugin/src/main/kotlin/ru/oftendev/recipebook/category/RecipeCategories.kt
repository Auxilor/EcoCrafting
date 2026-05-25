package ru.oftendev.recipebook.category

import com.willfp.eco.core.config.ConfigType
import com.willfp.eco.core.config.TransientConfig
import ru.oftendev.recipebook.recipe.VanillaRecipeScanner
import ru.oftendev.recipebook.recipeBookPlugin
import ru.oftendev.recipebook.validation.CategoryValidator
import java.io.File

object RecipeCategories {
    private val _registry: MutableList<RecipeCategory> = mutableListOf()
    val values: List<RecipeCategory> get() = _registry

    fun reload() {
        _registry.clear()

        val categoriesDir = File(recipeBookPlugin.dataFolder, "categories")
        categoriesDir.mkdirs()

        val files = categoriesDir.listFiles { file -> file.extension == "yml" } ?: return

        for (file in files) {
            val id = file.nameWithoutExtension
            val config = TransientConfig(file, ConfigType.YAML)
            config.set("id", id)
            runCatching { _registry.add(RecipeCategory(config)) }
                .onFailure { recipeBookPlugin.logger.warning("[RecipeBook] Failed to load category $id: ${it.message}") }
        }

        CategoryValidator.validate(_registry)
        VanillaRecipeScanner.populate(_registry)
    }

    fun getById(id: String?): RecipeCategory? {
        return id?.let { values.firstOrNull { it.id.equals(id, ignoreCase = true) } }
    }
}
