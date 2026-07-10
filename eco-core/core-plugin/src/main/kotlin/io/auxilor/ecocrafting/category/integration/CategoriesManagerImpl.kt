package io.auxilor.ecocrafting.category.integration

import io.auxilor.ecocrafting.api.category.Category
import io.auxilor.ecocrafting.api.category.CategoriesManager

// Thin adapter so CategoryLoader (a RegistrableCategory<RecipeCategory>, which already
// declares its own getByID(String): RecipeCategory?) doesn't need a return-type-clashing
// override to also satisfy the public API's getByID(String): Category?.
class CategoriesManagerImpl(private val categoryLoader: CategoryLoader) : CategoriesManager {
    override fun getByID(id: String): Category? = categoryLoader.getByID(id)
    override fun allCategories(): List<Category> = categoryLoader.values().toList()
}
