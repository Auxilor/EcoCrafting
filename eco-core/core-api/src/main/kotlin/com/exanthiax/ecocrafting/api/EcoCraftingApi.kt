package com.exanthiax.ecocrafting.api

import com.exanthiax.ecocrafting.api.category.CategoriesManager
import com.exanthiax.ecocrafting.api.recipe.RecipesManager
import com.exanthiax.ecocrafting.api.unlock.UnlockManager
import org.bukkit.Bukkit

interface EcoCraftingApi {
    fun categories(): CategoriesManager
    fun recipes(): RecipesManager
    fun unlocks(): UnlockManager

    companion object {
        @JvmStatic
        fun get(): EcoCraftingApi =
            Bukkit.getServicesManager().load(EcoCraftingApi::class.java)
                ?: error("EcoCraftingApi is not registered - is EcoCrafting enabled?")
    }
}
