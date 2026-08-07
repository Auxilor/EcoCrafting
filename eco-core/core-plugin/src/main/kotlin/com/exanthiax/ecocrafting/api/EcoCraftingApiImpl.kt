package com.exanthiax.ecocrafting.api

import com.exanthiax.ecocrafting.api.category.CategoriesManager
import com.exanthiax.ecocrafting.api.recipe.RecipesManager
import com.exanthiax.ecocrafting.api.unlock.UnlockManager

class EcoCraftingApiImpl(
    private val categoriesManager: CategoriesManager,
    private val recipesManager: RecipesManager,
    private val unlockManager: UnlockManager
) : EcoCraftingApi {
    override fun categories(): CategoriesManager = categoriesManager
    override fun recipes(): RecipesManager = recipesManager
    override fun unlocks(): UnlockManager = unlockManager
}
