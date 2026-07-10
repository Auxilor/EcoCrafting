package io.auxilor.ecocrafting.api

import io.auxilor.ecocrafting.api.category.CategoriesManager
import io.auxilor.ecocrafting.api.recipe.RecipesManager
import io.auxilor.ecocrafting.api.unlock.UnlockManager
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
