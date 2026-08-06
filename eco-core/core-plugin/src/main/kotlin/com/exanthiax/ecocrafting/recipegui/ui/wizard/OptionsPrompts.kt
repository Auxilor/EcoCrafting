package com.exanthiax.ecocrafting.recipegui.ui.wizard

import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.willfp.eco.util.formatEco
import com.exanthiax.ecocrafting.recipegui.service.WizardState
import com.exanthiax.ecocrafting.recipegui.ui.RecipeCreatorGUI
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player

// Chat-input prompts for the options screen's fields. Each one registers a one-shot
// handler in RecipeCreatorGUI.awaitingInput, consumed by RecipeCreatorChatListener.

internal fun RecipeCreatorGUI.promptPermissionValue(player: Player, state: WizardState) {
    player.sendMessage(
        "&aType a permission node, or &enone &ato clear it:".formatEco()
    )
    awaitingInput[player.uniqueId] = { input ->
        val trimmed = input.trim()
        state.permission = when {
            trimmed.equals("none", ignoreCase = true) -> ""
            trimmed.equals("default", ignoreCase = true) -> ""
            else -> trimmed
        }
        openOptions(player, state)
    }
}

internal fun RecipeCreatorGUI.promptCategory(player: Player, state: WizardState) {
    player.sendMessage(
        "&aType a category ID, or &enone &ato clear it:".formatEco()
    )
    awaitingInput[player.uniqueId] = { input ->
        val trimmed = input.trim()
        state.category = when {
            trimmed.equals("none", ignoreCase = true) -> ""
            trimmed.equals("default", ignoreCase = true) -> ""
            else -> trimmed
        }
        openOptions(player, state)
    }
}

internal fun RecipeCreatorGUI.promptCookTime(player: Player, state: WizardState) {
    player.sendMessage("&aType the cook time in ticks (e.g. 200), or &enone &afor the default:".formatEco())
    awaitingInput[player.uniqueId] = handler@{ input ->
        val trimmed = input.trim()
        if (trimmed.equals("none", ignoreCase = true) || trimmed.equals("default", ignoreCase = true)) {
            state.cookTime = null
            openOptions(player, state)
            return@handler
        }
        val ticks = trimmed.toIntOrNull()
        if (ticks == null || ticks <= 0) {
            player.sendMessage("&cInvalid cook time, try again.".formatEco())
            promptCookTime(player, state)
            return@handler
        }
        state.cookTime = ticks
        openOptions(player, state)
    }
}

internal fun RecipeCreatorGUI.promptRepairCost(player: Player, state: WizardState) {
    player.sendMessage("&aType the repair cost in levels (e.g. 3), or &enone &afor 1:".formatEco())
    awaitingInput[player.uniqueId] = handler@{ input ->
        val trimmed = input.trim()
        if (trimmed.equals("none", ignoreCase = true) || trimmed.equals("default", ignoreCase = true)) {
            state.repairCost = 1
            openOptions(player, state)
            return@handler
        }
        val cost = trimmed.toIntOrNull()
        if (cost == null || cost < 0) {
            player.sendMessage("&cInvalid repair cost, try again.".formatEco())
            promptRepairCost(player, state)
            return@handler
        }
        state.repairCost = cost
        openOptions(player, state)
    }
}

internal fun RecipeCreatorGUI.promptBrewTime(player: Player, state: WizardState) {
    player.sendMessage("&aType the brew time in ticks (e.g. 400), or &enone &afor the default:".formatEco())
    awaitingInput[player.uniqueId] = handler@{ input ->
        val trimmed = input.trim()
        if (trimmed.equals("none", ignoreCase = true) || trimmed.equals("default", ignoreCase = true)) {
            state.brewTime = null
            openOptions(player, state)
            return@handler
        }
        val ticks = trimmed.toIntOrNull()
        if (ticks == null || ticks <= 0) {
            player.sendMessage("&cInvalid brew time, try again.".formatEco())
            promptBrewTime(player, state)
            return@handler
        }
        state.brewTime = ticks
        openOptions(player, state)
    }
}

internal fun RecipeCreatorGUI.promptExperience(player: Player, state: WizardState) {
    player.sendMessage("&aType the XP given per craft (e.g. 0.35), or &enone &afor 0:".formatEco())
    awaitingInput[player.uniqueId] = handler@{ input ->
        val trimmed = input.trim()
        if (trimmed.equals("none", ignoreCase = true) || trimmed.equals("default", ignoreCase = true)) {
            state.experience = 0.0
            openOptions(player, state)
            return@handler
        }
        val xp = trimmed.toDoubleOrNull()
        if (xp == null || xp < 0) {
            player.sendMessage("&cInvalid XP value, try again.".formatEco())
            promptExperience(player, state)
            return@handler
        }
        state.experience = xp
        openOptions(player, state)
    }
}

internal fun RecipeCreatorGUI.promptProfession(player: Player, state: WizardState) {
    player.sendMessage(
        "&aType a villager profession (${villagerProfessionKeys.joinToString(", ")}), or &enone &afor any:".formatEco()
    )
    awaitingInput[player.uniqueId] = handler@{ input ->
        val trimmed = input.trim().lowercase()
        if (trimmed == "none" || trimmed == "default") {
            state.profession = ""
            openOptions(player, state)
            return@handler
        }
        if (trimmed !in villagerProfessionKeys) {
            player.sendMessage("&cUnknown profession, try again.".formatEco())
            promptProfession(player, state)
            return@handler
        }
        state.profession = trimmed
        openOptions(player, state)
    }
}

internal fun RecipeCreatorGUI.promptMinLevel(player: Player, state: WizardState) {
    player.sendMessage("&aType the minimum villager level required (1-5), or &enone &afor 0:".formatEco())
    awaitingInput[player.uniqueId] = handler@{ input ->
        val trimmed = input.trim()
        if (trimmed.equals("none", ignoreCase = true) || trimmed.equals("default", ignoreCase = true)) {
            state.minLevel = 0
            openOptions(player, state)
            return@handler
        }
        val level = trimmed.toIntOrNull()
        if (level == null || level < 0) {
            player.sendMessage("&cInvalid level, try again.".formatEco())
            promptMinLevel(player, state)
            return@handler
        }
        state.minLevel = level
        openOptions(player, state)
    }
}

internal fun RecipeCreatorGUI.promptChance(player: Player, state: WizardState) {
    player.sendMessage("&aType the trade chance (0.0-1.0), or &enone &afor 1.0:".formatEco())
    awaitingInput[player.uniqueId] = handler@{ input ->
        val trimmed = input.trim()
        if (trimmed.equals("none", ignoreCase = true) || trimmed.equals("default", ignoreCase = true)) {
            state.chance = 1.0
            openOptions(player, state)
            return@handler
        }
        val chance = trimmed.toDoubleOrNull()
        if (chance == null || chance < 0.0 || chance > 1.0) {
            player.sendMessage("&cInvalid chance, try again.".formatEco())
            promptChance(player, state)
            return@handler
        }
        state.chance = chance
        openOptions(player, state)
    }
}

internal fun RecipeCreatorGUI.promptVillagerXp(player: Player, state: WizardState) {
    player.sendMessage("&aType the XP given to the villager per trade, or &enone &afor 0:".formatEco())
    awaitingInput[player.uniqueId] = handler@{ input ->
        val trimmed = input.trim()
        if (trimmed.equals("none", ignoreCase = true) || trimmed.equals("default", ignoreCase = true)) {
            state.villagerXp = 0
            openOptions(player, state)
            return@handler
        }
        val xp = trimmed.toIntOrNull()
        if (xp == null || xp < 0) {
            player.sendMessage("&cInvalid XP value, try again.".formatEco())
            promptVillagerXp(player, state)
            return@handler
        }
        state.villagerXp = xp
        openOptions(player, state)
    }
}

internal fun RecipeCreatorGUI.promptId(player: Player, state: WizardState) {
    val existingId = state.editingId
    if (existingId != null) {
        openPreview(player, state.toPendingRecipe(existingId))
        return
    }
    player.sendMessage("&aType the recipe ID (lowercase, no spaces) in chat, or &ccancel &ato abort.".formatEco())
    awaitingInput[player.uniqueId] = handler@{ id ->
        val cleanId = id.lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_]"), "")
        if (cleanId.isBlank()) {
            player.sendMessage("&cID cannot be blank.".formatEco())
            promptId(player, state)
            return@handler
        }
        if (WorkstationRecipes.getByKey(NamespacedKey("ecocrafting", cleanId)) != null) {
            player.sendMessage("&cRecipe '$cleanId' already exists.".formatEco())
            promptId(player, state)
            return@handler
        }
        openPreview(player, state.toPendingRecipe(cleanId))
    }
}
