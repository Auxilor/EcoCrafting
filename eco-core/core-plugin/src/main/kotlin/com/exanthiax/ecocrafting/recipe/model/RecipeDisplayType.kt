package com.exanthiax.ecocrafting.recipe.model

enum class RecipeSource {
    ECO,
    BUKKIT,
    CUSTOM,
    UNKNOWN
}

enum class RecipeDisplayType {
    CRAFTING,
    SMELTING,
    BLAST_FURNACE,
    SMOKER,
    CAMPFIRE,
    SMITHING,
    STONECUTTER,
    CRAFTER,
    BREWING,
    GRINDSTONE,
    ANVIL,
    VILLAGER
}

val RecipeDisplayType.supportsCraftLimits: Boolean
    get() = this != RecipeDisplayType.BREWING
