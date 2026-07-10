package io.auxilor.ecocrafting.recipegui.service

import io.auxilor.ecocrafting.EcoCraftingPlugin
import io.auxilor.ecocrafting.quickcraft.service.QuickCraftService
import io.auxilor.ecocrafting.recipe.model.ResolvedRecipe
import io.auxilor.ecocrafting.recipe.service.RecipeResolverService
import io.auxilor.ecocrafting.recipe.service.RecipeService
import io.auxilor.ecocrafting.shop.service.ShopIntegrationService
import io.auxilor.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.entity.Player

// Parameter object bundling every collaborator the recipe-browsing GUIs need.
// Constructed once at the composition root and threaded explicitly through GUI
// constructors instead of each one taking five separate constructor parameters.
class RecipeGuiServices(
    val plugin: EcoCraftingPlugin,
    val recipeService: RecipeService,
    val resolverService: RecipeResolverService,
    val unlockService: RecipeUnlockService,
    val shopService: ShopIntegrationService
) {
    fun quickCraft(player: Player, recipe: ResolvedRecipe) =
        QuickCraftService(plugin, recipeService, resolverService, unlockService, player, recipe)
}
