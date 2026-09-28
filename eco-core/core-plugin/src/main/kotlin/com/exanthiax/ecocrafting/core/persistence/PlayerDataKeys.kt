package com.exanthiax.ecocrafting.core.persistence

import com.willfp.eco.core.data.keys.PersistentDataKey
import com.willfp.eco.core.data.keys.PersistentDataKeyType
import com.exanthiax.ecocrafting.EcoCraftingPlugin

class PlayerDataKeys(plugin: EcoCraftingPlugin) {
    val unlockedRecipes = PersistentDataKey(
        plugin.namespacedKeyFactory.create("unlocked_recipes"),
        PersistentDataKeyType.STRING_LIST,
        emptyList()
    )

    val lockedRecipeOverrides = PersistentDataKey(
        plugin.namespacedKeyFactory.create("locked_recipe_overrides"),
        PersistentDataKeyType.STRING_LIST,
        emptyList()
    )

    val craftLimitCounts = PersistentDataKey(
        plugin.namespacedKeyFactory.create("craft_limit_counts"),
        PersistentDataKeyType.STRING_LIST,
        emptyList()
    )

    val craftLimitGlobalCounts = PersistentDataKey(
        plugin.namespacedKeyFactory.create("craft_limit_global_counts"),
        PersistentDataKeyType.STRING_LIST,
        emptyList()
    )

    val craftLimitResets = PersistentDataKey(
        plugin.namespacedKeyFactory.create("craft_limit_resets"),
        PersistentDataKeyType.STRING_LIST,
        emptyList()
    )
}
