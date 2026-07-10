package io.auxilor.ecocrafting.recipe.integration

// Enforces free-version recipe caps per workstation `type` string.
// `crafting_table` gets a 10-recipe cap; every other type gets 5, tracked independently.
class RecipeCapEnforcer {
    private val registeredIdsByType = mutableMapOf<String, MutableSet<String>>()

    fun capFor(type: String): Int = if (type == "crafting_table") 10 else 5

    // Re-checking an already-registered id (e.g. on config reload) always succeeds
    // without consuming another slot.
    fun tryRegister(freeVersion: Boolean, type: String, id: String): Boolean {
        if (!freeVersion) return true
        val registered = registeredIdsByType.getOrPut(type) { mutableSetOf() }
        if (id in registered) return true
        if (registered.size >= capFor(type)) return false
        registered.add(id)
        return true
    }

    fun clear() {
        registeredIdsByType.clear()
    }
}
