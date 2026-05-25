package ru.oftendev.recipebook.validation

import ru.oftendev.recipebook.category.RecipeCategory
import ru.oftendev.recipebook.category.RecipeCategories
import ru.oftendev.recipebook.recipe.RecipeResolver
import ru.oftendev.recipebook.recipeBookPlugin

object CategoryValidator {
    fun validate(categories: List<RecipeCategory>): ValidationReport {
        val report = buildReport(categories)
        report.warnings.forEach { recipeBookPlugin.logger.warning("[RecipeBook] $it") }
        return report
    }

    fun buildReport(categories: List<RecipeCategory> = RecipeCategories.REGISTRY): ValidationReport {
        val warnings = mutableListOf<String>()
        val ids = categories.map { it.id.lowercase() }
        val duplicates = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        duplicates.forEach { warnings += "Duplicate category id: $it" }

        for (category in categories) {
            if (category.type !in setOf("items", "categories")) {
                warnings += "Category ${category.id} has invalid type '${category.type}'"
            }

            for (child in category.categories) {
                if (RecipeCategories.getById(child) == null) {
                    warnings += "Category ${category.id} references missing child category '$child'"
                }
            }

            validateGui(category, warnings)

            for (stack in category.items) {
                if (RecipeResolver.resolve(stack.item.item) == null) {
                    warnings += "Category ${category.id} item ${stack.item.item.type.name} has no supported recipe"
                }
            }
        }

        return ValidationReport(
            categories = categories.size,
            itemEntries = categories.sumOf { it.items.size },
            warnings = warnings
        )
    }

    private fun validateGui(category: RecipeCategory, warnings: MutableList<String>) {
        val pattern = category.config.getStrings("gui.mask.pattern")
        if (pattern.isEmpty()) {
            warnings += "Category ${category.id} has no gui.mask.pattern"
            return
        }
        val width = pattern.first().length
        if (width != 9 || pattern.any { it.length != width }) {
            warnings += "Category ${category.id} GUI mask should use equal-width 9-column rows"
        }
        if (pattern.size !in 1..6) {
            warnings += "Category ${category.id} GUI has invalid row count ${pattern.size}"
        }
    }
}

data class ValidationReport(
    val categories: Int,
    val itemEntries: Int,
    val warnings: List<String>
)
