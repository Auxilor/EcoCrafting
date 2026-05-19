package ru.oftendev.recipebook.custom

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.config.yaml.YamlBaseConfig
import com.willfp.eco.core.items.Items
import com.willfp.libreforge.SimpleHolder
import com.willfp.libreforge.conditions.ConditionList
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import ru.oftendev.recipebook.recipe.IngredientMatcher
import ru.oftendev.recipebook.recipe.RecipeIngredient
import ru.oftendev.recipebook.recipeBookPlugin
import java.io.File

object CustomRecipeLoader {

    fun load() {
        CustomRecipes.clear()
        val recipesDir = File(recipeBookPlugin.dataFolder, "recipes")
        if (!recipesDir.exists()) recipesDir.mkdirs()
        recipesDir.walkTopDown()
            .filter { it.isFile && it.extension == "yml" && !it.nameWithoutExtension.startsWith("_") }
            .forEach { file ->
                runCatching { loadFile(file) }
                    .onFailure { recipeBookPlugin.logger.warning("[RecipeBook] Failed to load recipe ${file.name}: ${it.message}") }
            }
    }

    private fun loadFile(file: File) {
        val config = YamlBaseConfig(file, recipeBookPlugin)
        val type = config.getString("type").lowercase()
        val recipe = when (type) {
            "crafting_table"    -> loadCraftingTable(file.nameWithoutExtension, config)
            "furnace"           -> loadSmelting(file.nameWithoutExtension, config, SmeltingType.FURNACE)
            "blast_furnace"     -> loadSmelting(file.nameWithoutExtension, config, SmeltingType.BLAST_FURNACE)
            "smoker"            -> loadSmelting(file.nameWithoutExtension, config, SmeltingType.SMOKER)
            "campfire"          -> loadSmelting(file.nameWithoutExtension, config, SmeltingType.CAMPFIRE)
            "smithing_table"    -> loadSmithing(file.nameWithoutExtension, config)
            "stonecutter"       -> loadStonecutter(file.nameWithoutExtension, config)
            "crafter"           -> loadCrafter(file.nameWithoutExtension, config)
            "brewing_stand"     -> loadBrewing(file.nameWithoutExtension, config)
            "cartography_table" -> loadCartography(file.nameWithoutExtension, config)
            "grindstone"        -> loadGrindstone(file.nameWithoutExtension, config)
            "anvil"             -> loadAnvil(file.nameWithoutExtension, config)
            "villager"          -> loadVillager(file.nameWithoutExtension, config)
            else -> error("Unknown recipe type: $type")
        }
        CustomRecipes.register(recipe)
        recipe.registerBukkit()
    }

    // ── Common helpers ───────────────────────────────────────────────────

    internal fun key(id: String) = NamespacedKey("recipebook", id.lowercase())

    internal fun parseIngredient(lookup: String): RecipeIngredient {
        if (lookup.isBlank()) return RecipeIngredient.empty(ItemStack(Material.AIR))
        val item = runCatching { Items.lookup(lookup).item }.getOrNull()
            ?: error("Cannot resolve item: $lookup")
        return RecipeIngredient(item.clone(), IngredientMatcher.SimilarItem(item.clone()))
    }

    internal fun parseCommonConditions(id: String, config: Config): CommonConditions {
        val permission = config.getStringOrNull("permission")?.takeIf { it.isNotBlank() }
        val visConds = recipeBookPlugin.compileConditions(
            config.getSubsections("visibility-conditions"), "visibility-conditions-$id", null
        )
        val craftConds = recipeBookPlugin.compileConditions(
            config.getSubsections("crafting-conditions"), "crafting-conditions-$id", null
        )
        val unlockConds = recipeBookPlugin.compileConditions(
            config.getSubsections("unlock-conditions"), "unlock-conditions-$id", null
        )
        return CommonConditions(
            permission = permission,
            visibilityConditions = ConditionList(visConds),
            craftingConditions = ConditionList(craftConds),
            lockedByDefault = config.getBool("locked-by-default"),
            showWhenLocked = config.getBool("show-when-locked"),
            lockedLore = config.getFormattedStrings("locked-lore"),
            unlockConditions = ConditionList(unlockConds)
        )
    }

    internal fun parseGhostHolder(id: String, config: Config): SimpleHolder? {
        if (!config.getBool("ghost")) return null
        val effects = recipeBookPlugin.compileEffects(
            config.getSubsections("effects"), "ghost-effects-$id", null
        )
        val conditions = recipeBookPlugin.compileConditions(
            config.getSubsections("conditions"), "ghost-conditions-$id", null
        )
        return SimpleHolder(key(id), effects, conditions)
    }

    // ── Type-specific parsers (added in Tasks 14–18) ─────────────────────

    private fun loadCraftingTable(id: String, config: Config): CustomRecipe = TODO()
    private fun loadSmelting(id: String, config: Config, type: SmeltingType): CustomRecipe = TODO()
    private fun loadSmithing(id: String, config: Config): CustomRecipe = TODO()
    private fun loadStonecutter(id: String, config: Config): CustomRecipe = TODO()
    private fun loadCrafter(id: String, config: Config): CustomRecipe = TODO()
    private fun loadBrewing(id: String, config: Config): CustomRecipe = TODO()
    private fun loadCartography(id: String, config: Config): CustomRecipe = TODO()
    private fun loadGrindstone(id: String, config: Config): CustomRecipe = TODO()
    private fun loadAnvil(id: String, config: Config): CustomRecipe = TODO()
    private fun loadVillager(id: String, config: Config): CustomRecipe = TODO()

    // ── Bukkit registration ──────────────────────────────────────────────

    internal fun CustomRecipe.registerBukkit() {
        // implemented in Task 14
    }
}

data class CommonConditions(
    val permission: String?,
    val visibilityConditions: ConditionList,
    val craftingConditions: ConditionList,
    val lockedByDefault: Boolean,
    val showWhenLocked: Boolean,
    val lockedLore: List<String>,
    val unlockConditions: ConditionList
)
