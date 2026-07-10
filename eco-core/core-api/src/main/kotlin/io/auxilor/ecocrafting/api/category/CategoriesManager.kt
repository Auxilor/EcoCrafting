package io.auxilor.ecocrafting.api.category

interface Category {
    val id: String
}

interface CategoriesManager {
    fun getByID(id: String): Category?
    fun allCategories(): List<Category>
}
