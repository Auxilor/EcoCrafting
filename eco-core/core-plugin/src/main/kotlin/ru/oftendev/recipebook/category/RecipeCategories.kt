package ru.oftendev.recipebook.category

import com.willfp.eco.core.config.ConfigType
import com.willfp.eco.core.config.TransientConfig
import ru.oftendev.recipebook.recipe.VanillaRecipeScanner
import ru.oftendev.recipebook.recipeBookPlugin
import ru.oftendev.recipebook.validation.CategoryValidator
import java.io.File

object RecipeCategories {
    val REGISTRY = mutableListOf<RecipeCategory>()

    private val defaultCategories = listOf("main.yml", "farming.yml", "mining.yml", "combat.yml", "utility.yml")

    fun reload() {
        REGISTRY.clear()

        val categoriesDir = File(recipeBookPlugin.dataFolder, "categories")

        if (!categoriesDir.exists() || categoriesDir.listFiles()?.isEmpty() != false) {
            categoriesDir.mkdirs()
            for (name in defaultCategories) {
                recipeBookPlugin.saveResource("categories/$name", false)
            }
        }

        val files = categoriesDir.listFiles { file -> file.extension == "yml" } ?: return

        for (file in files) {
            val id = file.nameWithoutExtension
            val config = TransientConfig(file, ConfigType.YAML)
            config.set("id", id)
            runCatching { REGISTRY.add(RecipeCategory(config)) }
                .onFailure { recipeBookPlugin.logger.warning("[RecipeBook] Failed to load category $id: ${it.message}") }
        }

        CategoryValidator.validate(REGISTRY)
        VanillaRecipeScanner.populate(REGISTRY)
    }

    fun getById(id: String?): RecipeCategory? {
        return id?.let { REGISTRY.firstOrNull { it.id.equals(id, true) } }
    }
}
