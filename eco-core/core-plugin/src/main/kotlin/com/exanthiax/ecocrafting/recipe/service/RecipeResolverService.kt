package com.exanthiax.ecocrafting.recipe.service

import com.willfp.eco.core.items.Items
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.recipe.model.RecipeDisplayType
import com.exanthiax.ecocrafting.recipe.model.RecipeIngredient
import com.exanthiax.ecocrafting.recipe.model.RecipeSource
import com.exanthiax.ecocrafting.recipe.model.RecipeSymmetry
import com.exanthiax.ecocrafting.recipe.model.ResolvedRecipe
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

// Resolves an ItemStack to whichever recipe produces it (eco's own registry, EcoCrafting's
// workstation recipes, or a plain Bukkit recipe) and checks permission to craft it. The
// three "external recipe -> ResolvedRecipe" mapping bodies live in their own files
// (EcoRecipeMapping / WorkstationRecipeMapping / BukkitRecipeMapping) since each is a
// sizeable per-type when-block in its own right.
class RecipeResolverService(
    private val plugin: EcoCraftingPlugin,
    private val recipeService: RecipeService
) {
    private val air = ItemStack(Material.AIR)

    // Bounded LRU so the cache can't grow unbounded over a long-running server.
    private val resolveCache = object : LinkedHashMap<Any, ResolvedRecipe?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, ResolvedRecipe?>): Boolean =
            size > 2000
    }

    private val symmetryTransforms = listOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8),
        RecipeSymmetry.ROT_90_CW,
        RecipeSymmetry.ROT_180,
        RecipeSymmetry.ROT_270_CW,
        RecipeSymmetry.MIRROR_H,
        intArrayOf(0, 3, 6, 1, 4, 7, 2, 5, 8), // transpose (MIRROR_H . ROT_90_CW)
        intArrayOf(6, 7, 8, 3, 4, 5, 0, 1, 2), // vertical flip (MIRROR_H . ROT_180)
        intArrayOf(8, 5, 2, 7, 4, 1, 6, 3, 0)  // anti-diagonal (MIRROR_H . ROT_270_CW)
    )

    private fun List<RecipeIngredient>.symmetryKey(): String {
        val types = map { it.displayItem.type.name }
        return symmetryTransforms
            .map { transform -> transform.map { types[it] }.joinToString(",") }
            .min()
    }

    private val recipeWildcardPermission = "ecocrafting.recipe.*"

    // Explicit `permission:` in the recipe config wins outright. Otherwise custom recipes
    // (ones with a stable id from RecipeLoader) fall back to a per-id node so server
    // owners can gate individual recipes without setting `permission:` on each one; that
    // node is covered by ecocrafting.recipe.* (default: true in plugin.yml) unless revoked.
    private fun effectivePermission(recipe: ResolvedRecipe): String? {
        recipe.permission?.let { return it }
        if (recipe.source != RecipeSource.CUSTOM) return null
        val id = recipe.key?.key ?: return null
        return "ecocrafting.recipe.$id"
    }

    fun clearCache() {
        resolveCache.clear()
    }

    fun warmCache(items: Collection<ItemStack>) {
        for (item in items) resolve(item)
    }

    fun hasAnyRecipe(itemStack: ItemStack): Boolean = resolve(itemStack) != null

    fun canCraft(player: Player, recipe: ResolvedRecipe): Boolean {
        val explicit = recipe.permission
        if (explicit != null) return player.hasPermission(explicit)
        val derived = effectivePermission(recipe) ?: return true
        return player.hasPermission(derived) || player.hasPermission(recipeWildcardPermission)
    }

    fun canCraft(player: Player, itemStack: ItemStack): Boolean {
        val recipe = resolve(itemStack) ?: return true
        return canCraft(player, recipe)
    }

    fun resolve(itemStack: ItemStack): ResolvedRecipe? {
        val clean = itemStack.clone().apply { amount = 1 }
        // Registered eco custom items cache by their stable key; everything else caches by
        // (Material, meta-hash) so NBT/meta-variants of the same Material (potions, renamed
        // items, enchanted books, ...) don't collapse onto one shared cache entry.
        val cacheKey: Any = Items.getCustomItem(clean)?.key
            ?: (clean.type to (if (clean.hasItemMeta()) clean.itemMeta.hashCode() else 0))
        if (resolveCache.containsKey(cacheKey)) return resolveCache[cacheKey]
        val result = resolveUncached(clean)
        resolveCache[cacheKey] = result
        return result
    }

    private fun resolveUncached(clean: ItemStack): ResolvedRecipe? {
        val customItem = Items.getCustomItem(clean)

        if (customItem != null) {
            findEcoRecipe(customItem, air, plugin)?.let { return it }
        }

        findWorkstationRecipeByOutput(clean)?.let { return it }

        return findBukkitRecipe(clean, air, plugin.configYml.getBool("strict-item-matching"))
    }

    fun resolveOutput(itemStack: ItemStack): ItemStack? {
        return resolve(itemStack)?.output?.clone()
    }

    fun resolveAll(itemStack: ItemStack): List<ResolvedRecipe> {
        val clean = itemStack.clone().apply { amount = 1 }
        val customItem = Items.getCustomItem(clean)
        val results = mutableListOf<ResolvedRecipe>()

        if (customItem != null) {
            findEcoRecipe(customItem, air, plugin)?.let { results += it }
        }

        findAllWorkstationRecipesByOutput(clean).forEach { workstation ->
            val existingIndex = results.indexOfFirst { it.key == workstation.key }
            if (existingIndex == -1) {
                results += workstation
            } else if (results[existingIndex].source != RecipeSource.CUSTOM) {
                // Same recipe registered under both eco's CraftingRecipe system and
                // WorkstationRecipes (e.g. crafting-table recipes). Prefer the CUSTOM
                // copy since only it carries lock-state via withLockState().
                results[existingIndex] = workstation
            }
        }

        val strict = plugin.configYml.getBool("strict-item-matching")
        findAllBukkitRecipes(clean, air, strict).forEach { bukkit ->
            val bukkitKey = bukkit.key
            // Collapse eco `_displayed`/`_crafter` ghosts and symmetry variants
            // (`<id>_rot90`, `<id>_rot90_displayed`, ...) onto the base recipe. The base is
            // already present as the CUSTOM entry, so only add a bukkit recipe that IS its
            // own base - i.e. a plain vanilla / other-plugin recipe.
            val base = bukkitKey?.let(recipeService::resolveBaseKey)
            val isVariantOrGhost = base != null && base != bukkitKey
            if (!isVariantOrGhost && results.none { it.key == bukkitKey }) results += bukkit
        }

        val sorted = results.sortedBy { it.displayType.name }

        if (!plugin.configYml.getBool("deduplicate-symmetrical")) return sorted

        val seen = mutableSetOf<String>()
        return sorted.filter { recipe ->
            val isShaped = recipe.displayType == RecipeDisplayType.CRAFTING ||
                recipe.displayType == RecipeDisplayType.CRAFTER
            if (!isShaped || recipe.shapeless) {
                true
            } else {
                val key = "${recipe.output.type.name}:${recipe.ingredients.symmetryKey()}"
                seen.add(key)
            }
        }
    }

    fun resolveForPlayer(itemStack: ItemStack, player: Player, unlockService: RecipeUnlockService): ResolvedRecipe? {
        val recipe = resolve(itemStack) ?: return null
        return recipe.withLockState(player, recipeService, unlockService)
    }

    private fun findWorkstationRecipeByOutput(clean: ItemStack): ResolvedRecipe? {
        return WorkstationRecipes.getAll()
            .firstOrNull { recipe -> recipe.output?.let { it.isSimilar(clean) } == true && recipeService.getMeta(recipe.key) != null }
            ?.let { it.toResolvedRecipe(air, recipeService.getMeta(it.key)) }
    }

    private fun findAllWorkstationRecipesByOutput(clean: ItemStack): List<ResolvedRecipe> {
        return WorkstationRecipes.getAll()
            .filter { recipe -> recipe.output?.let { it.isSimilar(clean) } == true && recipeService.getMeta(recipe.key) != null }
            .map { it.toResolvedRecipe(air, recipeService.getMeta(it.key)) }
    }
}
