package io.auxilor.ecocrafting.recipe.integration

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.TestableItem
import com.willfp.eco.core.recipe.recipes.ShapedCraftingRecipe
import com.willfp.eco.core.recipe.recipes.ShapelessCraftingRecipe
import com.willfp.eco.core.recipe.workstation.AnvilRecipe
import com.willfp.eco.core.recipe.workstation.BrewingRecipe
import com.willfp.eco.core.recipe.workstation.CrafterRecipe
import com.willfp.eco.core.recipe.workstation.GrindstoneRecipe
import com.willfp.eco.core.recipe.workstation.SmeltingRecipe
import com.willfp.eco.core.recipe.workstation.SmithingRecipe
import com.willfp.eco.core.recipe.workstation.StonecuttingRecipe
import com.willfp.eco.core.recipe.workstation.VillagerRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.willfp.libreforge.ViolationContext
import com.willfp.libreforge.conditions.Conditions
import com.willfp.libreforge.effects.Effects
import io.auxilor.ecocrafting.recipe.model.EcoCraftingMeta
import io.auxilor.ecocrafting.recipe.model.IngredientMatcher
import io.auxilor.ecocrafting.recipe.model.RecipeDisplayType
import io.auxilor.ecocrafting.recipe.model.RecipeIngredient
import io.auxilor.ecocrafting.recipe.model.toTestableItem
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.inventory.ItemStack
import com.willfp.eco.core.recipe.workstation.SmeltingType as WSmeltingType

// Per-workstation-type config parsing, split out of RecipeLoader itself (which owns the
// shared parse*/register* helpers these call into) purely to keep each file under the
// project's file-size convention - there is no independent state here.

private fun Config.permissionOrNull(): String? =
    getStringOrNull("permission")?.takeIf { it.isNotBlank() }

@Suppress("UNCHECKED_CAST")
private fun RecipeLoader.crafterParts(ingredients: List<RecipeIngredient>) =
    ingredients.map { if (it.matcher == IngredientMatcher.AnyItem) null else it.matcher.toTestableItem() }
        as List<TestableItem>

@Suppress("UNCHECKED_CAST")
private fun RecipeLoader.crafterDisplays(ingredients: List<RecipeIngredient>) =
    ingredients.map { if (it.matcher == IngredientMatcher.AnyItem) null else it.displayItem }
        as List<ItemStack>

internal fun RecipeLoader.loadCraftingTable(id: String, config: Config) {
    val rawParts = config.getStrings("recipe")
    require(rawParts.size == 9) { "crafting_table recipe must have exactly 9 entries, got ${rawParts.size}" }
    val ingredients = rawParts.map { parseIngredient(it) }
    val output = parseOutputItem(config)
    val shapeless = config.getBool("shapeless")
    val symmetry = config.getBool("symmetry")
    val permission = config.permissionOrNull()
    val meta = parseMeta(id, config, RecipeDisplayType.CRAFTING)
    val baseKey = key(id)

    fun registerEcoVariant(variantKey: NamespacedKey, recipeParts: List<RecipeIngredient>) {
        if (shapeless) {
            val builder = ShapelessCraftingRecipe
                .builder(plugin, variantKey.key)
                .setOutput(output)
                .setCrafterSupported(meta.supportCrafter)
            recipeParts
                .filter { !it.empty && it.matcher !is IngredientMatcher.AnyItem }
                .forEach { builder.addRecipePart(it.matcher.toTestableItem()) }
            builder.build().register()
        } else {
            val builder = ShapedCraftingRecipe
                .builder(plugin, variantKey.key)
                .setOutput(output)
                .setCrafterSupported(meta.supportCrafter)
            recipeParts.forEachIndexed { index, part ->
                if (!part.empty) builder.setRecipePart(index, part.matcher.toTestableItem())
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
            recipeService.registerVariant(variantKey, baseKey)
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
    recipeService.register(baseKey, meta)
}

internal fun RecipeLoader.loadSmelting(id: String, config: Config, type: WSmeltingType) {
    val ingredient = parseIngredient(config.getString("input"))
    val recipe = SmeltingRecipe.builder(key(id), parseOutputItem(config), ingredient.matcher.toTestableItem(), type)
        .inputDisplay(ingredient.displayItem)
        .cookTime(if (config.has("cook-time")) config.getInt("cook-time") else -1)
        .experience(config.getStringOrNull("experience")?.toFloatOrNull() ?: 0f)
        .also { builder -> config.permissionOrNull()?.let { builder.permission(it) } }
        .build()
    val displayType = when (type) {
        WSmeltingType.FURNACE       -> RecipeDisplayType.SMELTING
        WSmeltingType.BLAST_FURNACE -> RecipeDisplayType.BLAST_FURNACE
        WSmeltingType.SMOKER        -> RecipeDisplayType.SMOKER
        WSmeltingType.CAMPFIRE      -> RecipeDisplayType.CAMPFIRE
    }
    registerWithMeta(recipe, parseMeta(id, config, displayType))
}

internal fun RecipeLoader.loadSmithing(id: String, config: Config) {
    val template = parseIngredient(config.getString("template"))
    val base = parseIngredient(config.getString("base"))
    val addition = parseIngredient(config.getString("addition"))
    val recipe = SmithingRecipe.builder(key(id), parseOutputItem(config))
        .template(template.matcher.toTestableItem(), template.displayItem)
        .base(base.matcher.toTestableItem(), base.displayItem)
        .addition(addition.matcher.toTestableItem(), addition.displayItem)
        .also { builder -> config.permissionOrNull()?.let { builder.permission(it) } }
        .build()
    registerWithMeta(recipe, parseMeta(id, config, RecipeDisplayType.SMITHING))
}

internal fun RecipeLoader.loadStonecutter(id: String, config: Config) {
    val input = parseIngredient(config.getString("input"))
    val ctx = ViolationContext(plugin, "recipe-$id")
    val rawOutputs = config.getSubsections("outputs")
    require(rawOutputs.isNotEmpty()) { "stonecutter '$id' must have at least one output" }

    rawOutputs.forEachIndexed { index, outputConfig ->
        val outKey = NamespacedKey("ecocrafting", "${id.lowercase()}_$index")
        val outItem = run {
            val base = Items.lookup(outputConfig.getString("item")).item
            val lore = outputConfig.getFormattedStrings("lore")
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
            .also { builder -> config.permissionOrNull()?.let { builder.permission(it) } }
            .build()
        val giveResultItem = if (outputConfig.has("give-result-item")) outputConfig.getBool("give-result-item") else true
        val effectsChain = Effects.compileChain(outputConfig.getSubsections("effects"), ctx.with("effects-$index"))
        val outMeta = EcoCraftingMeta(
            giveResultItem = giveResultItem,
            effectsChain = effectsChain,
            visibilityConditions = Conditions.compile(config.getSubsections("visibility-conditions"), ctx.with("visibility-conditions")),
            craftingConditions = Conditions.compile(config.getSubsections("crafting-conditions"), ctx.with("crafting-conditions")),
            lockedByDefault = config.getBool("locked-by-default"),
            showWhenLocked = config.getBool("show-when-locked"),
            lockedLore = config.getFormattedStrings("locked-lore"),
            unlockConditions = Conditions.compile(config.getSubsections("unlock-conditions"), ctx.with("unlock-conditions")),
            displayType = RecipeDisplayType.STONECUTTER,
            categoryId = config.getStringOrNull("category")?.takeIf { it.isNotBlank() },
            price = parsePrice(outputConfig)
        )
        registerWithMeta(recipe, outMeta)
    }
}

internal fun RecipeLoader.loadBrewing(id: String, config: Config) {
    val base = parseIngredient(config.getStringOrNull("base") ?: "")
    val ingredient = parseIngredient(config.getString("ingredient"))
    val recipe = BrewingRecipe.builder(key(id), parseOutputItem(config), base.matcher.toTestableItem(), ingredient.matcher.toTestableItem())
        .brewTime(config.getIntOrNull("brew-time") ?: 400)
        .also { builder -> config.permissionOrNull()?.let { builder.permission(it) } }
        .build()
    registerWithMeta(recipe, parseMeta(id, config, RecipeDisplayType.BREWING))
}

internal fun RecipeLoader.loadGrindstone(id: String, config: Config) {
    val item1 = parseIngredient(config.getString("item1"))
    val item2 = config.getStringOrNull("item2")?.let { parseIngredient(it) }
    val recipe = GrindstoneRecipe.builder(key(id), parseOutputItem(config), item1.matcher.toTestableItem())
        .item2(item2?.matcher?.toTestableItem())
        .also { builder -> config.permissionOrNull()?.let { builder.permission(it) } }
        .build()
    registerWithMeta(recipe, parseMeta(id, config, RecipeDisplayType.GRINDSTONE))
}

internal fun RecipeLoader.loadAnvil(id: String, config: Config) {
    val base = parseIngredient(config.getString("base"))
    val material = config.getStringOrNull("material")?.let { parseIngredient(it) }
    val recipe = AnvilRecipe.builder(key(id), parseOutputItem(config), base.matcher.toTestableItem())
        .material(material?.matcher?.toTestableItem())
        .resultName(config.getStringOrNull("result-name")?.takeIf { it.isNotBlank() })
        .repairCost(config.getIntOrNull("repair-cost") ?: 1)
        .also { builder -> config.permissionOrNull()?.let { builder.permission(it) } }
        .build()
    registerWithMeta(recipe, parseMeta(id, config, RecipeDisplayType.ANVIL))
}

internal fun RecipeLoader.loadVillager(id: String, config: Config) {
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
        .also { builder -> config.permissionOrNull()?.let { builder.permission(it) } }
        .build()
    registerWithMeta(recipe, parseMeta(id, config, RecipeDisplayType.VILLAGER))
}
