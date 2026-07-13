package io.auxilor.ecocrafting.api

import io.auxilor.ecocrafting.api.category.CategoriesManager
import io.auxilor.ecocrafting.api.recipe.RecipesManager
import io.auxilor.ecocrafting.api.unlock.UnlockManager

class EcoCraftingApiImpl(
    private val categoriesManager: CategoriesManager,
    private val recipesManager: RecipesManager,
    private val unlockManager: UnlockManager
) : EcoCraftingApi {
    override fun categories(): CategoriesManager = categoriesManager
    override fun recipes(): RecipesManager = recipesManager
    override fun unlocks(): UnlockManager = unlockManager
}
