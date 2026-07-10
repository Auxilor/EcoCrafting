package io.auxilor.ecocrafting.recipegui.service

import io.auxilor.ecocrafting.recipe.model.IngredientMatcher
import io.auxilor.ecocrafting.recipe.model.RecipeDisplayType
import io.auxilor.ecocrafting.recipe.model.RecipeIngredient
import io.auxilor.ecocrafting.recipe.model.RecipeSource
import io.auxilor.ecocrafting.recipe.model.ResolvedRecipe
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

// Mutable in-progress state for one player's recipe-creation wizard session.
class WizardState(
    val typeKey: String,
    val parts: Map<Int, ItemStack>,
    val output: ItemStack,
    initialShapeless: Boolean,
    initialSymmetry: Boolean,
    initialSupportCrafter: Boolean,
    val editingId: String?,
    val editingPermission: String?
) {
    var shapeless: Boolean = initialShapeless
    var symmetry: Boolean = initialSymmetry
    var supportCrafter: Boolean = initialSupportCrafter
    var cookTime: Int? = null
    var experience: Double = 0.0
    var profession: String = ""
    var minLevel: Int = 0
    var chance: Double = 1.0
    var wanderingTrader: Boolean = false
    var villagerXp: Int = 0
    var permission: String = editingPermission ?: ""
    var category: String = ""
    var lockedByDefault: Boolean = false
    var showWhenLocked: Boolean = false
    var repairCost: Int = 1
    var brewTime: Int? = null

    fun toPendingRecipe(id: String) = PendingRecipe(
        typeKey = typeKey,
        parts = parts,
        output = output,
        shapeless = shapeless,
        symmetry = symmetry,
        supportCrafter = supportCrafter,
        cookTime = cookTime,
        experience = experience,
        profession = profession,
        minLevel = minLevel,
        chance = chance,
        wanderingTrader = wanderingTrader,
        villagerXp = villagerXp,
        id = id,
        permission = permission,
        category = category,
        lockedByDefault = lockedByDefault,
        showWhenLocked = showWhenLocked,
        repairCost = repairCost,
        brewTime = brewTime
    )
}

data class PendingRecipe(
    val typeKey: String,
    val parts: Map<Int, ItemStack>,
    val output: ItemStack,
    val shapeless: Boolean,
    val symmetry: Boolean,
    val supportCrafter: Boolean,
    val cookTime: Int?,
    val experience: Double,
    val profession: String,
    val minLevel: Int,
    val chance: Double,
    val wanderingTrader: Boolean,
    val villagerXp: Int,
    val id: String,
    val permission: String,
    val category: String,
    val lockedByDefault: Boolean,
    val showWhenLocked: Boolean,
    val repairCost: Int,
    val brewTime: Int?
)

internal fun PendingRecipe.toPreviewResolvedRecipe(): ResolvedRecipe {
    fun ingredient(item: ItemStack?): RecipeIngredient =
        if (item == null || item.type.isAir) RecipeIngredient.empty(ItemStack(Material.AIR))
        else RecipeIngredient(item.clone(), IngredientMatcher.SimilarItem(item.clone()))

    val ingredients: List<RecipeIngredient> = if (typeKey == "crafting_table" || typeKey == "crafter") {
        (0..8).map { ingredient(parts[it]) }
    } else {
        val ordered = parts.entries.sortedBy { it.key }.map { ingredient(it.value) }
        (ordered + List(9) { ingredient(null) }).take(9)
    }

    val displayType = when (typeKey) {
        "crafting_table"  -> RecipeDisplayType.CRAFTING
        "crafter"         -> RecipeDisplayType.CRAFTER
        "furnace"         -> RecipeDisplayType.SMELTING
        "blast_furnace"   -> RecipeDisplayType.BLAST_FURNACE
        "smoker"          -> RecipeDisplayType.SMOKER
        "campfire"        -> RecipeDisplayType.CAMPFIRE
        "smithing_table"  -> RecipeDisplayType.SMITHING
        "stonecutter"     -> RecipeDisplayType.STONECUTTER
        "brewing_stand"   -> RecipeDisplayType.BREWING
        "grindstone"      -> RecipeDisplayType.GRINDSTONE
        "anvil"           -> RecipeDisplayType.ANVIL
        "villager"        -> RecipeDisplayType.VILLAGER
        else              -> RecipeDisplayType.CRAFTING
    }

    return ResolvedRecipe(
        key = null,
        output = output.clone(),
        ingredients = ingredients,
        permission = permission.takeIf { it.isNotBlank() },
        source = RecipeSource.CUSTOM,
        shapeless = shapeless,
        displayType = displayType,
        cookTime = cookTime,
        villagerXp = villagerXp.takeIf { it > 0 }
    )
}
