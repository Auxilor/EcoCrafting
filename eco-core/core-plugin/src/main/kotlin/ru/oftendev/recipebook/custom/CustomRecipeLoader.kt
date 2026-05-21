package ru.oftendev.recipebook.custom

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.recipe.workstation.AnvilRecipe
import com.willfp.eco.core.recipe.workstation.BrewingRecipe
import com.willfp.eco.core.recipe.workstation.CrafterRecipe
import com.willfp.eco.core.recipe.workstation.GrindstoneRecipe
import com.willfp.eco.core.recipe.workstation.SmeltingRecipe
import com.willfp.eco.core.recipe.workstation.SmithingRecipe
import com.willfp.eco.core.recipe.workstation.StonecuttingRecipe
import com.willfp.eco.core.recipe.workstation.VillagerRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.willfp.eco.core.recipe.workstation.SmeltingType as WSmeltingType
import com.willfp.libreforge.ViolationContext
import com.willfp.libreforge.conditions.Conditions
import com.willfp.libreforge.effects.Effects
import com.willfp.libreforge.loader.LibreforgePlugin
import com.willfp.libreforge.loader.configs.ConfigCategory
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.recipe.IngredientMatcher
import ru.oftendev.recipebook.recipe.RecipeDisplayType
import ru.oftendev.recipebook.recipe.RecipeIngredient
import ru.oftendev.recipebook.recipe.toTestableItem
import ru.oftendev.recipebook.recipeBookPlugin

object CustomRecipeLoader : ConfigCategory("recipe", "recipes") {

    override fun clear(plugin: LibreforgePlugin) {
        WorkstationRecipes.clear()
        CustomRecipes.clear()
    }

    override fun acceptConfig(plugin: LibreforgePlugin, id: String, config: Config) {
        if (config.has("enabled") && !config.getBool("enabled")) return
        val type = config.getString("type").lowercase()
        runCatching {
            when (type) {
                "crafting_table"  -> loadCraftingTable(id, config)
                "furnace"         -> loadSmelting(id, config, WSmeltingType.FURNACE)
                "blast_furnace"   -> loadSmelting(id, config, WSmeltingType.BLAST_FURNACE)
                "smoker"          -> loadSmelting(id, config, WSmeltingType.SMOKER)
                "campfire"        -> loadSmelting(id, config, WSmeltingType.CAMPFIRE)
                "smithing_table"  -> loadSmithing(id, config)
                "stonecutter"     -> loadStonecutter(id, config)
                "crafter"         -> loadCrafter(id, config)
                "brewing_stand"   -> loadBrewing(id, config)
                "grindstone"      -> loadGrindstone(id, config)
                "anvil"           -> loadAnvil(id, config)
                "villager"        -> loadVillager(id, config)
                else -> error("Unknown recipe type: $type")
            }
        }.onFailure {
            recipeBookPlugin.logger.warning("[RecipeBook] Failed to load recipe $id: ${it.message}")
        }
    }

    override fun afterReload(plugin: LibreforgePlugin) {
        if (recipeBookPlugin.configYml.getBool("villager-scan-on-reload")) scanVillagers()
    }

    private fun scanVillagers() {
        val validKeyNames = WorkstationRecipes.getAll(VillagerRecipe::class.java)
            .map { "vr_${it.key.key}" }.toSet()
        org.bukkit.Bukkit.getWorlds().flatMap { it.entities }
            .filterIsInstance<org.bukkit.entity.AbstractVillager>()
            .forEach { villager ->
                val pdc = villager.persistentDataContainer
                pdc.keys.filter { it.namespace == "recipebook" && it.key.startsWith("vr_") }
                    .filter { it.key !in validKeyNames }
                    .forEach { pdc.remove(it) }
            }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    internal fun key(id: String) = NamespacedKey("recipebook", id.lowercase())

    internal fun parseOutputItem(config: Config): ItemStack {
        val item = runCatching { Items.lookup(config.getString("output")).item }.getOrNull()
            ?: error("Cannot resolve output: ${config.getString("output")}")
        val lore = config.getFormattedStrings("lore")
        if (lore.isNotEmpty()) {
            val meta = item.itemMeta ?: return item
            meta.lore(lore.map {
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(it)
            })
            item.itemMeta = meta
        }
        return item
    }

    internal fun parseIngredient(lookup: String): RecipeIngredient {
        if (lookup.isBlank() || lookup == "*")
            return RecipeIngredient(ItemStack(Material.AIR), IngredientMatcher.AnyItem)
        val item = runCatching { Items.lookup(lookup).item }.getOrNull()
            ?: error("Cannot resolve item: $lookup")
        return RecipeIngredient(item.clone(), IngredientMatcher.SimilarItem(item.clone()))
    }

    internal fun parseMeta(id: String, config: Config, displayType: RecipeDisplayType): RecipeBookMeta {
        val ctx = ViolationContext(recipeBookPlugin, "recipe-$id")
        val giveResultItem = config.getBool("give-result-item")
        val effectsChain = Effects.compileChain(config.getSubsections("effects"), ctx.with("effects"))
        return RecipeBookMeta(
            giveResultItem = giveResultItem,
            effectsChain = effectsChain,
            visibilityConditions = Conditions.compile(config.getSubsections("visibility-conditions"), ctx.with("visibility-conditions")),
            craftingConditions = Conditions.compile(config.getSubsections("crafting-conditions"), ctx.with("crafting-conditions")),
            lockedByDefault = config.getBool("locked-by-default"),
            showWhenLocked = config.getBool("show-when-locked"),
            lockedLore = config.getFormattedStrings("locked-lore"),
            unlockConditions = Conditions.compile(config.getSubsections("unlock-conditions"), ctx.with("unlock-conditions")),
            displayType = displayType
        )
    }

    private fun registerWithMeta(recipe: WorkstationRecipe, meta: RecipeBookMeta) {
        recipe.register()
        CustomRecipes.register(recipe.key, meta)
    }

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
        addVariant("_rot90",  RecipeSymmetry.ROT_90_CW)
        addVariant("_rot180", RecipeSymmetry.ROT_180)
        addVariant("_rot270", RecipeSymmetry.ROT_270_CW)
        addVariant("_mir",    RecipeSymmetry.MIRROR_H)
        addVariant("_mir90",  RecipeSymmetry.MIRROR_H.map { RecipeSymmetry.ROT_90_CW[it] }.toIntArray())
        addVariant("_mir180", RecipeSymmetry.MIRROR_H.map { RecipeSymmetry.ROT_180[it] }.toIntArray())
        addVariant("_mir270", RecipeSymmetry.MIRROR_H.map { RecipeSymmetry.ROT_270_CW[it] }.toIntArray())
        return variants
    }

    // ── Type-specific loaders ────────────────────────────────────────────

    private fun loadCraftingTable(id: String, config: Config) {
        val rawParts = config.getStrings("recipe")
        require(rawParts.size == 9) { "crafting_table recipe must have exactly 9 entries, got ${rawParts.size}" }
        val ingredients = rawParts.map { parseIngredient(it) }
        val output = parseOutputItem(config)
        val shapeless = config.getBool("shapeless")
        val symmetry = config.getBool("symmetry")
        val permission = config.getStringOrNull("permission")?.takeIf { it.isNotBlank() }
        val meta = parseMeta(id, config, RecipeDisplayType.CRAFTING)
        val baseKey = key(id)

        fun registerEcoVariant(variantKey: NamespacedKey, recipeParts: List<RecipeIngredient>) {
            if (shapeless) {
                val builder = com.willfp.eco.core.recipe.recipes.ShapelessCraftingRecipe
                    .builder(recipeBookPlugin, variantKey.key).setOutput(output)
                recipeParts.filter { !it.empty }.forEach { builder.addRecipePart(it.matcher.toTestableItem()) }
                builder.build().register()
            } else {
                val builder = com.willfp.eco.core.recipe.recipes.ShapedCraftingRecipe
                    .builder(recipeBookPlugin, variantKey.key).setOutput(output)
                recipeParts.forEachIndexed { idx, part ->
                    if (!part.empty) builder.setRecipePart(idx, part.matcher.toTestableItem())
                }
                builder.build().register()
            }
            WorkstationRecipes.trackBukkitKey(variantKey)
        }

        // Register via eco's shaped/shapeless recipe system for crafting table
        registerEcoVariant(baseKey, ingredients)
        if (symmetry && !shapeless) {
            generateSymmetryVariants(ingredients).forEach { (suffix, variantParts) ->
                registerEcoVariant(NamespacedKey("recipebook", "${baseKey.key}$suffix"), variantParts)
            }
        }

        // Register a CrafterRecipe stub in WorkstationRecipes for listener key lookup
        val crafterStub = CrafterRecipe.builder(baseKey, output)
            .parts(ingredients.map { it.matcher.toTestableItem() }, ingredients.map { it.displayItem })
            .shapeless(shapeless)
            .also { b -> permission?.let { b.permission(it) } }
            .build()
        WorkstationRecipes.register(crafterStub)
        CustomRecipes.register(baseKey, meta)
    }

    private fun loadSmelting(id: String, config: Config, type: WSmeltingType) {
        val ingredient = parseIngredient(config.getString("input"))
        val recipe = SmeltingRecipe.builder(key(id), parseOutputItem(config), ingredient.matcher.toTestableItem(), type)
            .inputDisplay(ingredient.displayItem)
            .cookTime(if (config.has("cook-time")) config.getInt("cook-time") else -1)
            .experience(config.getStringOrNull("experience")?.toFloatOrNull() ?: 0f)
            .also { b -> config.getStringOrNull("permission")?.takeIf { it.isNotBlank() }?.let { b.permission(it) } }
            .build()
        registerWithMeta(recipe, parseMeta(id, config, RecipeDisplayType.SMELTING))
    }

    private fun loadSmithing(id: String, config: Config) {
        val template = parseIngredient(config.getString("template"))
        val base = parseIngredient(config.getString("base"))
        val addition = parseIngredient(config.getString("addition"))
        val recipe = SmithingRecipe.builder(key(id), parseOutputItem(config))
            .template(template.matcher.toTestableItem(), template.displayItem)
            .base(base.matcher.toTestableItem(), base.displayItem)
            .addition(addition.matcher.toTestableItem(), addition.displayItem)
            .also { b -> config.getStringOrNull("permission")?.takeIf { it.isNotBlank() }?.let { b.permission(it) } }
            .build()
        registerWithMeta(recipe, parseMeta(id, config, RecipeDisplayType.SMITHING))
    }

    private fun loadStonecutter(id: String, config: Config) {
        val input = parseIngredient(config.getString("input"))
        val ctx = ViolationContext(recipeBookPlugin, "recipe-$id")
        val rawOutputs = config.getSubsections("outputs")
        require(rawOutputs.isNotEmpty()) { "stonecutter '$id' must have at least one output" }

        rawOutputs.forEachIndexed { idx, outCfg ->
            val outKey = NamespacedKey("recipebook", "${id.lowercase()}_$idx")
            val outItem = run {
                val base = runCatching { Items.lookup(outCfg.getString("item")).item }.getOrNull()
                    ?: error("Cannot resolve stonecutter output: ${outCfg.getString("item")}")
                val lore = outCfg.getFormattedStrings("lore")
                if (lore.isNotEmpty()) {
                    val itemMeta = base.itemMeta ?: return@run base
                    itemMeta.lore(lore.map {
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(it)
                    })
                    base.itemMeta = itemMeta
                }
                base
            }
            val recipe = StonecuttingRecipe.builder(outKey, outItem, input.matcher.toTestableItem())
                .inputDisplay(input.displayItem)
                .also { b -> config.getStringOrNull("permission")?.takeIf { it.isNotBlank() }?.let { b.permission(it) } }
                .build()
            val giveResultItem = outCfg.getBool("give-result-item")
            val effectsChain = Effects.compileChain(outCfg.getSubsections("effects"), ctx.with("effects-$idx"))
            val outMeta = RecipeBookMeta(
                giveResultItem = giveResultItem, effectsChain = effectsChain,
                visibilityConditions = Conditions.compile(config.getSubsections("visibility-conditions"), ctx.with("visibility-conditions")),
                craftingConditions = Conditions.compile(config.getSubsections("crafting-conditions"), ctx.with("crafting-conditions")),
                lockedByDefault = config.getBool("locked-by-default"),
                showWhenLocked = config.getBool("show-when-locked"),
                lockedLore = config.getFormattedStrings("locked-lore"),
                unlockConditions = Conditions.compile(config.getSubsections("unlock-conditions"), ctx.with("unlock-conditions")),
                displayType = RecipeDisplayType.STONECUTTER
            )
            registerWithMeta(recipe, outMeta)
        }
    }

    private fun loadCrafter(id: String, config: Config) {
        val rawParts = config.getStrings("recipe")
        require(rawParts.size == 9) { "crafter recipe must have exactly 9 entries" }
        val ingredients = rawParts.map { parseIngredient(it) }
        val recipe = CrafterRecipe.builder(key(id), parseOutputItem(config))
            .parts(ingredients.map { it.matcher.toTestableItem() }, ingredients.map { it.displayItem })
            .shapeless(config.getBool("shapeless"))
            .also { b -> config.getStringOrNull("permission")?.takeIf { it.isNotBlank() }?.let { b.permission(it) } }
            .build()
        registerWithMeta(recipe, parseMeta(id, config, RecipeDisplayType.CRAFTER))
    }

    private fun loadBrewing(id: String, config: Config) {
        val base = parseIngredient(config.getStringOrNull("base") ?: "")
        val ingredient = parseIngredient(config.getString("ingredient"))
        val recipe = BrewingRecipe.builder(key(id), parseOutputItem(config), base.matcher.toTestableItem(), ingredient.matcher.toTestableItem())
            .brewTime(config.getIntOrNull("brew-time") ?: 400)
            .also { b -> config.getStringOrNull("permission")?.takeIf { it.isNotBlank() }?.let { b.permission(it) } }
            .build()
        registerWithMeta(recipe, parseMeta(id, config, RecipeDisplayType.BREWING))
    }

    private fun loadGrindstone(id: String, config: Config) {
        val item1 = parseIngredient(config.getString("item1"))
        val item2 = config.getStringOrNull("item2")?.let { parseIngredient(it) }
        val recipe = GrindstoneRecipe.builder(key(id), parseOutputItem(config), item1.matcher.toTestableItem())
            .item2(item2?.matcher?.toTestableItem())
            .also { b -> config.getStringOrNull("permission")?.takeIf { it.isNotBlank() }?.let { b.permission(it) } }
            .build()
        registerWithMeta(recipe, parseMeta(id, config, RecipeDisplayType.GRINDSTONE))
    }

    private fun loadAnvil(id: String, config: Config) {
        val base = parseIngredient(config.getString("base"))
        val material = config.getStringOrNull("material")?.let { parseIngredient(it) }
        val recipe = AnvilRecipe.builder(key(id), parseOutputItem(config), base.matcher.toTestableItem())
            .material(material?.matcher?.toTestableItem())
            .resultName(config.getStringOrNull("result-name")?.takeIf { it.isNotBlank() })
            .repairCost(config.getIntOrNull("repair-cost") ?: 1)
            .also { b -> config.getStringOrNull("permission")?.takeIf { it.isNotBlank() }?.let { b.permission(it) } }
            .build()
        registerWithMeta(recipe, parseMeta(id, config, RecipeDisplayType.ANVIL))
    }

    private fun loadVillager(id: String, config: Config) {
        val input1 = parseIngredient(config.getString("input1"))
        val input2 = config.getStringOrNull("input2")?.let { parseIngredient(it) }
        val profession = config.getStringOrNull("profession")?.let {
            runCatching { org.bukkit.entity.Villager.Profession.valueOf(it.uppercase()) }.getOrNull()
        }
        val recipe = VillagerRecipe.builder(key(id), parseOutputItem(config), input1.matcher.toTestableItem())
            .input1Display(input1.displayItem)
            .input2(input2?.matcher?.toTestableItem())
            .input2Display(input2?.displayItem)
            .profession(profession)
            .minLevel(config.getIntOrNull("min-level") ?: 0)
            .chance((config.getStringOrNull("chance")?.toDoubleOrNull() ?: 1.0).coerceIn(0.0, 1.0))
            .wanderingTrader(config.getBool("wandering-trader"))
            .also { b -> config.getStringOrNull("permission")?.takeIf { it.isNotBlank() }?.let { b.permission(it) } }
            .build()
        registerWithMeta(recipe, parseMeta(id, config, RecipeDisplayType.VILLAGER))
    }
}
