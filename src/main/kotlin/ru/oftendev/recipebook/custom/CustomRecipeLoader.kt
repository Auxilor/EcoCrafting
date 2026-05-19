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
import ru.oftendev.recipebook.recipe.toTestableItem
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

    // Flat 9-element index remappings for shaped recipe symmetry variants.
    // Original grid indices: 0 1 2 / 3 4 5 / 6 7 8
    internal val ROT_90_CW  = intArrayOf(6, 3, 0, 7, 4, 1, 8, 5, 2)
    internal val ROT_180    = intArrayOf(8, 7, 6, 5, 4, 3, 2, 1, 0)
    internal val ROT_270_CW = intArrayOf(2, 5, 8, 1, 4, 7, 0, 3, 6)
    internal val MIRROR_H   = intArrayOf(2, 1, 0, 5, 4, 3, 8, 7, 6)

    internal fun generateSymmetryVariants(parts: List<RecipeIngredient>): List<Pair<String, List<RecipeIngredient>>> {
        val variants = mutableListOf<Pair<String, List<RecipeIngredient>>>()
        val seen = mutableSetOf<List<Int>>()
        fun fingerprint(p: List<RecipeIngredient>) = p.indices.filter { !p[it].empty }

        fun addVariant(suffix: String, remap: IntArray) {
            val remapped = remap.map { parts[it] }
            val fp = fingerprint(remapped)
            if (seen.add(fp)) variants.add(suffix to remapped)
        }

        seen.add(fingerprint(parts))
        addVariant("_rot90",  ROT_90_CW)
        addVariant("_rot180", ROT_180)
        addVariant("_rot270", ROT_270_CW)
        addVariant("_mir",    MIRROR_H)
        addVariant("_mir90",  MIRROR_H.map { ROT_90_CW[it] }.toIntArray())
        addVariant("_mir180", MIRROR_H.map { ROT_180[it] }.toIntArray())
        addVariant("_mir270", MIRROR_H.map { ROT_270_CW[it] }.toIntArray())
        return variants
    }

    private fun loadCraftingTable(id: String, config: Config): CustomRecipe {
        val rawParts = config.getStrings("recipe")
        require(rawParts.size == 9) { "crafting_table recipe must have exactly 9 entries, got ${rawParts.size}" }
        val parts = rawParts.map { parseIngredient(it) }
        val cc = parseCommonConditions(id, config)
        return CustomRecipe.CraftingTable(
            key = key(id),
            output = Items.lookup(config.getString("output")).item,
            parts = parts,
            shapeless = config.getBool("shapeless"),
            symmetry = config.getBool("symmetry"),
            permission = cc.permission,
            ghost = config.getBool("ghost"),
            ghostHolder = parseGhostHolder(id, config),
            visibilityConditions = cc.visibilityConditions,
            craftingConditions = cc.craftingConditions,
            lockedByDefault = cc.lockedByDefault,
            showWhenLocked = cc.showWhenLocked,
            lockedLore = cc.lockedLore,
            unlockConditions = cc.unlockConditions
        )
    }
    private fun loadSmelting(id: String, config: Config, type: SmeltingType): CustomRecipe {
        val cc = parseCommonConditions(id, config)
        return CustomRecipe.Smelting(
            key = key(id),
            output = Items.lookup(config.getString("output")).item,
            input = parseIngredient(config.getString("input")),
            stationType = type,
            cookTime = if (config.has("cook-time")) config.getInt("cook-time") else -1,
            experience = config.getFloatOrNull("experience") ?: 0f,
            permission = cc.permission,
            ghost = config.getBool("ghost"),
            ghostHolder = parseGhostHolder(id, config),
            visibilityConditions = cc.visibilityConditions,
            craftingConditions = cc.craftingConditions,
            lockedByDefault = cc.lockedByDefault,
            showWhenLocked = cc.showWhenLocked,
            lockedLore = cc.lockedLore,
            unlockConditions = cc.unlockConditions
        )
    }

    private fun loadSmithing(id: String, config: Config): CustomRecipe {
        val cc = parseCommonConditions(id, config)
        return CustomRecipe.Smithing(
            key = key(id),
            output = Items.lookup(config.getString("output")).item,
            template = parseIngredient(config.getString("template")),
            base = parseIngredient(config.getString("base")),
            addition = parseIngredient(config.getString("addition")),
            permission = cc.permission,
            ghost = config.getBool("ghost"),
            ghostHolder = parseGhostHolder(id, config),
            visibilityConditions = cc.visibilityConditions,
            craftingConditions = cc.craftingConditions,
            lockedByDefault = cc.lockedByDefault,
            showWhenLocked = cc.showWhenLocked,
            lockedLore = cc.lockedLore,
            unlockConditions = cc.unlockConditions
        )
    }

    private fun loadStonecutter(id: String, config: Config): CustomRecipe {
        val cc = parseCommonConditions(id, config)
        val input = parseIngredient(config.getString("input"))
        val rawOutputs = config.getSubsections("outputs")
        require(rawOutputs.isNotEmpty()) { "stonecutter recipe '$id' must have at least one output" }
        val outputs = rawOutputs.mapIndexed { idx, outCfg ->
            val ghost = outCfg.getBool("ghost")
            val ghostHolder: SimpleHolder? = if (ghost) {
                val effects = recipeBookPlugin.compileEffects(outCfg.getSubsections("effects"), "sc-effects-$id-$idx", null)
                val conditions = recipeBookPlugin.compileConditions(outCfg.getSubsections("conditions"), "sc-conds-$id-$idx", null)
                SimpleHolder(NamespacedKey("recipebook", "${id}_out$idx"), effects, conditions)
            } else null
            StonecutterOutput(
                item = Items.lookup(outCfg.getString("item")).item,
                ghost = ghost,
                ghostHolder = ghostHolder
            )
        }
        return CustomRecipe.Stonecutter(
            key = key(id),
            input = input,
            outputs = outputs,
            permission = cc.permission,
            visibilityConditions = cc.visibilityConditions,
            craftingConditions = cc.craftingConditions,
            lockedByDefault = cc.lockedByDefault,
            showWhenLocked = cc.showWhenLocked,
            lockedLore = cc.lockedLore,
            unlockConditions = cc.unlockConditions
        )
    }

    private fun loadCrafter(id: String, config: Config): CustomRecipe {
        val cc = parseCommonConditions(id, config)
        val rawParts = config.getStrings("recipe")
        require(rawParts.size == 9) { "crafter recipe must have exactly 9 entries" }
        return CustomRecipe.Crafter(
            key = key(id),
            output = Items.lookup(config.getString("output")).item,
            parts = rawParts.map { parseIngredient(it) },
            shapeless = config.getBool("shapeless"),
            permission = cc.permission,
            visibilityConditions = cc.visibilityConditions,
            craftingConditions = cc.craftingConditions
        )
    }

    private fun loadBrewing(id: String, config: Config): CustomRecipe {
        val cc = parseCommonConditions(id, config)
        return CustomRecipe.Brewing(
            key = key(id),
            output = Items.lookup(config.getString("output")).item,
            base = parseIngredient(config.getString("base")),
            ingredient = parseIngredient(config.getString("ingredient")),
            permission = cc.permission,
            ghost = config.getBool("ghost"),
            ghostHolder = parseGhostHolder(id, config),
            visibilityConditions = cc.visibilityConditions,
            craftingConditions = cc.craftingConditions,
            lockedByDefault = cc.lockedByDefault,
            showWhenLocked = cc.showWhenLocked,
            lockedLore = cc.lockedLore,
            unlockConditions = cc.unlockConditions
        )
    }

    private fun loadCartography(id: String, config: Config): CustomRecipe {
        val cc = parseCommonConditions(id, config)
        return CustomRecipe.Cartography(
            key = key(id),
            output = Items.lookup(config.getString("output")).item,
            map = parseIngredient(config.getString("map")),
            addition = parseIngredient(config.getString("addition")),
            permission = cc.permission,
            ghost = config.getBool("ghost"),
            ghostHolder = parseGhostHolder(id, config),
            visibilityConditions = cc.visibilityConditions,
            craftingConditions = cc.craftingConditions,
            lockedByDefault = cc.lockedByDefault,
            showWhenLocked = cc.showWhenLocked,
            lockedLore = cc.lockedLore,
            unlockConditions = cc.unlockConditions
        )
    }

    private fun loadGrindstone(id: String, config: Config): CustomRecipe {
        val cc = parseCommonConditions(id, config)
        return CustomRecipe.Grindstone(
            key = key(id),
            output = Items.lookup(config.getString("output")).item,
            item1 = parseIngredient(config.getString("item1")),
            item2 = config.getStringOrNull("item2")?.let { parseIngredient(it) },
            permission = cc.permission,
            ghost = config.getBool("ghost"),
            ghostHolder = parseGhostHolder(id, config),
            visibilityConditions = cc.visibilityConditions,
            craftingConditions = cc.craftingConditions,
            lockedByDefault = cc.lockedByDefault,
            showWhenLocked = cc.showWhenLocked,
            lockedLore = cc.lockedLore,
            unlockConditions = cc.unlockConditions
        )
    }

    private fun loadAnvil(id: String, config: Config): CustomRecipe {
        val cc = parseCommonConditions(id, config)
        return CustomRecipe.Anvil(
            key = key(id),
            output = Items.lookup(config.getString("output")).item,
            base = parseIngredient(config.getString("base")),
            material = config.getStringOrNull("material")?.let { parseIngredient(it) },
            resultName = config.getStringOrNull("result-name")?.takeIf { it.isNotBlank() },
            repairCost = config.getIntOrNull("repair-cost") ?: 1,
            permission = cc.permission,
            ghost = config.getBool("ghost"),
            ghostHolder = parseGhostHolder(id, config),
            visibilityConditions = cc.visibilityConditions,
            craftingConditions = cc.craftingConditions,
            lockedByDefault = cc.lockedByDefault,
            showWhenLocked = cc.showWhenLocked,
            lockedLore = cc.lockedLore,
            unlockConditions = cc.unlockConditions
        )
    }

    private fun loadVillager(id: String, config: Config): CustomRecipe {
        val cc = parseCommonConditions(id, config)
        return CustomRecipe.Villager(
            key = key(id),
            output = Items.lookup(config.getString("output")).item,
            input1 = parseIngredient(config.getString("input1")),
            input2 = config.getStringOrNull("input2")?.let { parseIngredient(it) },
            permission = cc.permission,
            ghost = config.getBool("ghost"),
            ghostHolder = parseGhostHolder(id, config),
            visibilityConditions = cc.visibilityConditions,
            craftingConditions = cc.craftingConditions,
            lockedByDefault = cc.lockedByDefault,
            showWhenLocked = cc.showWhenLocked,
            lockedLore = cc.lockedLore,
            unlockConditions = cc.unlockConditions
        )
    }

    // ── Bukkit registration ──────────────────────────────────────────────

    internal fun CustomRecipe.registerBukkit() {
        when (this) {
            is CustomRecipe.CraftingTable -> registerCraftingTable()
            is CustomRecipe.Smelting      -> registerSmelting()
            is CustomRecipe.Smithing      -> registerSmithing()
            is CustomRecipe.Stonecutter   -> registerStonecutter()
            is CustomRecipe.Crafter       -> registerCrafter()
            else -> { /* Group B — no Bukkit recipe needed */ }
        }
    }

    private fun CustomRecipe.CraftingTable.registerCraftingTable() {
        fun register(recipeKey: NamespacedKey, recipeParts: List<RecipeIngredient>) {
            if (shapeless) {
                val builder = com.willfp.eco.core.recipe.recipes.ShapelessCraftingRecipe
                    .builder(recipeBookPlugin, recipeKey.key)
                    .setOutput(output)
                recipeParts.filter { !it.empty }.forEach { builder.addRecipePart(it.matcher.toTestableItem()) }
                builder.build().register()
            } else {
                val builder = com.willfp.eco.core.recipe.recipes.ShapedCraftingRecipe
                    .builder(recipeBookPlugin, recipeKey.key)
                    .setOutput(output)
                recipeParts.forEachIndexed { idx, part ->
                    if (!part.empty) builder.setRecipePart(idx, part.matcher.toTestableItem())
                }
                builder.build().register()
            }
        }

        register(key, parts)
        if (symmetry && !shapeless) {
            generateSymmetryVariants(parts).forEach { (suffix, variantParts) ->
                register(NamespacedKey("recipebook", "${key.key}$suffix"), variantParts)
            }
        }
    }

    private fun CustomRecipe.Smelting.registerSmelting() {
        val recipeKey = key
        val inputChoice = org.bukkit.inventory.RecipeChoice.ExactChoice(input.displayItem)
        val out = output.clone()
        val cookTime = if (cookTime < 0) null else cookTime
        val xp = experience
        when (stationType) {
            SmeltingType.FURNACE -> org.bukkit.Bukkit.addRecipe(
                org.bukkit.inventory.FurnaceRecipe(recipeKey, out, inputChoice, xp, cookTime ?: 200))
            SmeltingType.BLAST_FURNACE -> org.bukkit.Bukkit.addRecipe(
                org.bukkit.inventory.BlastingRecipe(recipeKey, out, inputChoice, xp, cookTime ?: 100))
            SmeltingType.SMOKER -> org.bukkit.Bukkit.addRecipe(
                org.bukkit.inventory.SmokingRecipe(recipeKey, out, inputChoice, xp, cookTime ?: 100))
            SmeltingType.CAMPFIRE -> org.bukkit.Bukkit.addRecipe(
                org.bukkit.inventory.CampfireRecipe(recipeKey, out, inputChoice, xp, cookTime ?: 600))
        }
    }

    private fun CustomRecipe.Smithing.registerSmithing() {
        val r = org.bukkit.inventory.SmithingTransformRecipe(
            key,
            output.clone(),
            org.bukkit.inventory.RecipeChoice.ExactChoice(template.displayItem),
            org.bukkit.inventory.RecipeChoice.ExactChoice(base.displayItem),
            org.bukkit.inventory.RecipeChoice.ExactChoice(addition.displayItem)
        )
        org.bukkit.Bukkit.addRecipe(r)
    }

    private fun CustomRecipe.Stonecutter.registerStonecutter() {
        outputs.forEachIndexed { idx, out ->
            val r = org.bukkit.inventory.StonecuttingRecipe(
                NamespacedKey("recipebook", "${key.key}_$idx"),
                out.item.clone(),
                org.bukkit.inventory.RecipeChoice.ExactChoice(input.displayItem)
            )
            org.bukkit.Bukkit.addRecipe(r)
        }
    }

    private fun CustomRecipe.Crafter.registerCrafter() {
        val builder = com.willfp.eco.core.recipe.recipes.ShapedCraftingRecipe
            .builder(recipeBookPlugin, key.key)
            .setOutput(output)
        parts.forEachIndexed { idx, part ->
            if (!part.empty) builder.setRecipePart(idx, part.matcher.toTestableItem())
        }
        builder.build().register()
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
