package ru.oftendev.recipebook.category

object RecipeCategories {
    private val _registry: MutableList<RecipeCategory> = mutableListOf()
    val values: List<RecipeCategory> get() = _registry

    internal fun clear() {
        _registry.clear()
    }

    internal fun register(category: RecipeCategory) {
        _registry.add(category)
    }

    fun getById(id: String?): RecipeCategory? {
        return id?.let { values.firstOrNull { it.id.equals(id, ignoreCase = true) } }
    }
}
