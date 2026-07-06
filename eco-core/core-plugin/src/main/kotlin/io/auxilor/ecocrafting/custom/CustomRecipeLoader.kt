package io.auxilor.ecocrafting.custom

import org.bukkit.Registry
import org.bukkit.entity.AbstractVillager
import org.bukkit.Bukkit
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import com.willfp.eco.core.recipe.recipes.ShapelessCraftingRecipe
import com.willfp.eco.core.recipe.recipes.ShapedCraftingRecipe
import com.willfp.eco.core.recipe.parts.GroupedTestableItems
import com.willfp.eco.core.recipe.parts.EmptyTestableItem
import com.willfp.eco.core.items.TestableItem
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
import io.auxilor.ecocrafting.category.RecipeCategories
import io.auxilor.ecocrafting.recipe.IngredientMatcher
import io.auxilor.ecocrafting.recipe.RecipeDisplayType
import io.auxilor.ecocrafting.recipe.RecipeIngredient
import io.auxilor.ecocrafting.recipe.toTestableItem
import io.auxilor.ecocrafting.plugin

object CustomRecipeLoader : ConfigCategory("recipe", "recipes") {

    override fun clear(plugin: LibreforgePlugin) {
        WorkstationRecipes.clear()
        CustomRecipes.clear()
    }

    override fun acceptConfig(plugin: LibreforgePlugin, id: String, config: Config) {
        if (config.has("enabled") && !config.getBool("enabled")) return
        val type = config.getString("type").lowercase()
        try {
            when (type) {
                "crafting_table"  -> loadCraftingTable(id, config)
                "furnace"         -> loadSmelting(id, config, WSmeltingType.FURNACE)
                "blast_furnace"   -> loadSmelting(id, config, WSmeltingType.BLAST_FURNACE)
                "smoker"          -> loadSmelting(id, config, WSmeltingType.SMOKER)
                "campfire"        -> loadSmelting(id, config, WSmeltingType.CAMPFIRE)
                "smithing_table"  -> loadSmithing(id, config)
                "stonecutter"     -> loadStonecutter(id, config)
                "brewing_stand"   -> loadBrewing(id, config)
                "grindstone"      -> loadGrindstone(id, config)
                "anvil"           -> loadAnvil(id, config)
                "villager"        -> loadVillager(id, config)
                else -> error("Unknown recipe type: $type")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load recipe $id: ${e.message}")
        }
    }

    override fun afterReload(plugin: LibreforgePlugin) {
        if (plugin.configYml.getBool("villager-scan-on-reload")) scanVillagers()
        CustomRecipes.allKeys().forEach { key ->
            val categoryId = CustomRecipes.getMeta(key)?.categoryId ?: return@forEach
            val output = WorkstationRecipes.getAll().firstOrNull { it.key == key }?.output ?: return@forEach
            val category = RecipeCategories.getById(categoryId)
            if (category != null) {
                category.registerCustomRecipe(output.clone())
            } else {
                plugin.logger.fine("[EcoCrafting] Unknown category '$categoryId' for recipe '${key.key}', skipping")
            }
        }
    }

    private fun scanVillagers() {
        val validKeyNames = WorkstationRecipes.getAll(VillagerRecipe::class.java)
            .map { "vr_${it.key.key}" }.toSet()
        Bukkit.getWorlds().flatMap { it.entities }
            .filterIsInstance<AbstractVillager>()
            .forEach { villager ->
                val pdc = villager.persistentDataContainer
                pdc.keys.filter { it.namespace == "ecocrafting" && it.key.startsWith("vr_") }
                    .filter { it.key !in validKeyNames }
                    .forEach { pdc.remove(it) }
            }
    }

    // Helpers
    internal fun key(id: String) = NamespacedKey("ecocrafting", id.lowercase())

    internal fun parseOutputItem(config: Config): ItemStack {
        val item = Items.lookup(config.getString("output")).item
        val lore = config.getFormattedStrings("lore")
        if (lore.isNotEmpty()) {
            val meta = item.itemMeta ?: return item
            meta.lore(lore.map {
                LegacyComponentSerializer.legacySection().deserialize(it)
            })
            item.itemMeta = meta
        }
        return item
    }

    internal fun parseIngredient(lookup: String): RecipeIngredient {
        if (lookup.isBlank() || lookup == "*")
            return RecipeIngredient(ItemStack(Material.AIR), IngredientMatcher.AnyItem)
        val testable = Items.lookup(lookup)
        if (testable is EmptyTestableItem)
            error("Cannot resolve item: $lookup")
        val displayItems = if (testable is GroupedTestableItems && testable.children.isNotEmpty())
            testable.children.map { it.item.clone() }
        else
            listOf(testable.item.clone())
        val matcher = if (testable is GroupedTestableItems && testable.children.isNotEmpty())
            IngredientMatcher.EcoPart(testable)
        else
            IngredientMatcher.SimilarItem(displayItems.first())
        return RecipeIngredient(
            displayItem = displayItems.first(),
            matcher = matcher,
            displayAlternatives = if (displayItems.size > 1) displayItems else emptyList()
        )
    }

    internal fun parseMeta(id: String, config: Config, displayType: RecipeDisplayType): EcoCraftingMeta {
        val ctx = ViolationContext(plugin, "recipe-$id")
        val giveResultItem = if (config.has("give-result-item")) config.getBool("give-result-item") else true
        val effectsChain = Effects.compileChain(config.getSubsections("effects"), ctx.with("effects"))
        return EcoCraftingMeta(
            giveResultItem = giveResultItem,
            effectsChain = effectsChain,
            visibilityConditions = Conditions.compile(config.getSubsections("visibility-conditions"), ctx.with("visibility-conditions")),
            craftingConditions = Conditions.compile(config.getSubsections("crafting-conditions"), ctx.with("crafting-conditions")),
            lockedByDefault = config.getBool("locked-by-default"),
            showWhenLocked = config.getBool("show-when-locked"),
            lockedLore = config.getFormattedStrings("locked-lore"),
            unlockConditions = Conditions.compile(config.getSubsections("unlock-conditions"), ctx.with("unlock-conditions")),
            displayType = displayType,
            supportCrafter = config.getBool("support-crafter"),
            categoryId = config.getStringOrNull("category")?.takeIf { it.isNotBlank() }
        )
    }

    private fun registerWithMeta(recipe: WorkstationRecipe, meta: EcoCraftingMeta) {
        recipe.register()
        CustomRecipes.register(recipe.key, meta)
    }

    internal fun generateSymmetryVariants(parts: List<RecipeIngredient>): List<Pair<String, List<RecipeIngredient>>> {
        val variants = mutableListOf<Pair<String, List<RecipeIngredient>>>()
        val seen = mutableSetOf<List<Int>>()
        fun fingerprint(ingredients: List<RecipeIngredient>) = ingredients.indices.filter { !ingredients[it].empty }
        fun addVariant(suffix: String, remap: IntArray) {
            val remapped = remap.map { parts[it] }
            val signature = fingerprint(remapped)
            if (seen.add(signature)) variants.add(suffix to remapped)
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

    // Type-specific loaders
    @Suppress("UNCHECKED_CAST")
    private fun crafterParts(ingredients: List<RecipeIngredient>) =
        ingredients.map { if (it.matcher == IngredientMatcher.AnyItem) null else it.matcher.toTestableItem() }
            as List<TestableItem>

    @Suppress("UNCHECKED_CAST")
    private fun crafterDisplays(ingredients: List<RecipeIngredient>) =
        ingredients.map { if (it.matcher == IngredientMatcher.AnyItem) null else it.displayItem }
            as List<ItemStack>

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
                val builder = ShapelessCraftingRecipe
                    .builder(plugin, variantKey.key)
                    .setOutput(output)
                    .setSupportCrafter(meta.supportCrafter)
                recipeParts
                    .filter { !it.empty && it.matcher !is IngredientMatcher.AnyItem }
                    .forEach { builder.addRecipePart(it.matcher.toTestableItem()) }
                builder.build().register()
            } else {
                val builder = ShapedCraftingRecipe
                    .builder(plugin, variantKey.key)
                    .setOutput(output)
                    .setSupportCrafter(meta.supportCrafter)
                recipeParts.forEachIndexed { idx, part ->
                    if (!part.empty) builder.setRecipePart(idx, part.matcher.toTestableItem())
                }
                builder.build().register()
            }
            WorkstationRecipes.trackBukkitKey(variantKey)
            if (meta.supportCrafter) {
                WorkstationRecipes.trackBukkitKey(NamespacedKey(variantKey.namespace, "${variantKey.key}_crafter"))
            }
        }

        // Register via eco's shaped/shapeless recipe system for crafting table.
        // Eco now handles the <key>_crafter Bukkit registration when
        // setSupportCrafter(true) is set on the builder.
        registerEcoVariant(baseKey, ingredients)
        if (symmetry && !shapeless) {
            generateSymmetryVariants(ingredients).forEach { (suffix, variantParts) ->
                val variantKey = NamespacedKey("ecocrafting", "${baseKey.key}$suffix")
                registerEcoVariant(variantKey, variantParts)
                CustomRecipes.registerVariant(variantKey, baseKey)
            }
        }

        // CrafterRecipe entry in WorkstationRecipes is for listener key lookup
        // (matrix-fallback when vanilla wins at Bukkit, or _crafter suffix lookup).
        val crafterStub = CrafterRecipe.builder(baseKey, output)
            .parts(crafterParts(ingredients), crafterDisplays(ingredients))
            .shapeless(shapeless)
            .also { builder -> permission?.let { builder.permission(it) } }
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
            .also { builder -> config.getStringOrNull("permission")?.takeIf { it.isNotBlank() }?.let { builder.permission(it) } }
            .build()
        val displayType = when (type) {
            WSmeltingType.FURNACE       -> RecipeDisplayType.SMELTING
            WSmeltingType.BLAST_FURNACE -> RecipeDisplayType.BLAST_FURNACE
            WSmeltingType.SMOKER        -> RecipeDisplayType.SMOKER
            WSmeltingType.CAMPFIRE      -> RecipeDisplayType.CAMPFIRE
        }
        registerWithMeta(recipe, parseMeta(id, config, displayType))
    }

    private fun loadSmithing(id: String, config: Config) {
        val template = parseIngredient(config.getString("template"))
        val base = parseIngredient(config.getString("base"))
        val addition = parseIngredient(config.getString("addition"))
        val recipe = SmithingRecipe.builder(key(id), parseOutputItem(config))
            .template(template.matcher.toTestableItem(), template.displayItem)
            .base(base.matcher.toTestableItem(), base.displayItem)
            .addition(addition.matcher.toTestableItem(), addition.displayItem)
            .also { builder -> config.getStringOrNull("permission")?.takeIf { it.isNotBlank() }?.let { builder.permission(it) } }
            .build()
        registerWithMeta(recipe, parseMeta(id, config, RecipeDisplayType.SMITHING))
    }

    private fun loadStonecutter(id: String, config: Config) {
        val input = parseIngredient(config.getString("input"))
        val ctx = ViolationContext(plugin, "recipe-$id")
        val rawOutputs = config.getSubsections("outputs")
        require(rawOutputs.isNotEmpty()) { "stonecutter '$id' must have at least one output" }

        rawOutputs.forEachIndexed { idx, outCfg ->
            val outKey = NamespacedKey("ecocrafting", "${id.lowercase()}_$idx")
            val outItem = run {
                val base = Items.lookup(outCfg.getString("item")).item
                val lore = outCfg.getFormattedStrings("lore")
                if (lore.isNotEmpty()) {
                    val itemMeta = base.itemMeta ?: return@run base
                    itemMeta.lore(lore.map {
                        LegacyComponentSerializer.legacySection().deserialize(it)
                    })
                    base.itemMeta = itemMeta
                }
                base
            }
            val recipe = StonecuttingRecipe.builder(outKey, outItem, input.matcher.toTestableItem())
                .inputDisplay(input.displayItem)
                .also { builder -> config.getStringOrNull("permission")?.takeIf { it.isNotBlank() }?.let { builder.permission(it) } }
                .build()
            val giveResultItem = if (outCfg.has("give-result-item")) outCfg.getBool("give-result-item") else true
            val effectsChain = Effects.compileChain(outCfg.getSubsections("effects"), ctx.with("effects-$idx"))
            val outMeta = EcoCraftingMeta(
                giveResultItem = giveResultItem, effectsChain = effectsChain,
                visibilityConditions = Conditions.compile(config.getSubsections("visibility-conditions"), ctx.with("visibility-conditions")),
                craftingConditions = Conditions.compile(config.getSubsections("crafting-conditions"), ctx.with("crafting-conditions")),
                lockedByDefault = config.getBool("locked-by-default"),
                showWhenLocked = config.getBool("show-when-locked"),
                lockedLore = config.getFormattedStrings("locked-lore"),
                unlockConditions = Conditions.compile(config.getSubsections("unlock-conditions"), ctx.with("unlock-conditions")),
                displayType = RecipeDisplayType.STONECUTTER,
                categoryId = config.getStringOrNull("category")?.takeIf { it.isNotBlank() }
            )
            registerWithMeta(recipe, outMeta)
        }
    }

    private fun loadBrewing(id: String, config: Config) {
        val base = parseIngredient(config.getStringOrNull("base") ?: "")
        val ingredient = parseIngredient(config.getString("ingredient"))
        val recipe = BrewingRecipe.builder(key(id), parseOutputItem(config), base.matcher.toTestableItem(), ingredient.matcher.toTestableItem())
            .brewTime(config.getIntOrNull("brew-time") ?: 400)
            .also { builder -> config.getStringOrNull("permission")?.takeIf { it.isNotBlank() }?.let { builder.permission(it) } }
            .build()
        registerWithMeta(recipe, parseMeta(id, config, RecipeDisplayType.BREWING))
    }

    private fun loadGrindstone(id: String, config: Config) {
        val item1 = parseIngredient(config.getString("item1"))
        val item2 = config.getStringOrNull("item2")?.let { parseIngredient(it) }
        val recipe = GrindstoneRecipe.builder(key(id), parseOutputItem(config), item1.matcher.toTestableItem())
            .item2(item2?.matcher?.toTestableItem())
            .also { builder -> config.getStringOrNull("permission")?.takeIf { it.isNotBlank() }?.let { builder.permission(it) } }
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
            .also { builder -> config.getStringOrNull("permission")?.takeIf { it.isNotBlank() }?.let { builder.permission(it) } }
            .build()
        registerWithMeta(recipe, parseMeta(id, config, RecipeDisplayType.ANVIL))
    }

    private fun loadVillager(id: String, config: Config) {
        val input1 = parseIngredient(config.getString("input1"))
        val input2 = config.getStringOrNull("input2")?.let { parseIngredient(it) }
        val profession = config.getStringOrNull("profession")
            ?.let { Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(it.lowercase())) }
        val recipe = VillagerRecipe.builder(key(id), parseOutputItem(config), input1.matcher.toTestableItem())
            .input1Display(input1.displayItem)
            .input2(input2?.matcher?.toTestableItem())
            .input2Display(input2?.displayItem)
            .profession(profession)
            .minLevel(config.getIntOrNull("min-level") ?: 0)
            .chance((config.getStringOrNull("chance")?.toDoubleOrNull() ?: 1.0).coerceIn(0.0, 1.0))
            .wanderingTrader(config.getBool("wandering-trader"))
            .villagerXp(config.getIntOrNull("villager-xp") ?: 0)
            .also { builder -> config.getStringOrNull("permission")?.takeIf { it.isNotBlank() }?.let { builder.permission(it) } }
            .build()
        registerWithMeta(recipe, parseMeta(id, config, RecipeDisplayType.VILLAGER))
    }
}
