package io.auxilor.ecocrafting.recipe.integration

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.price.ConfiguredPrice
import com.willfp.eco.core.recipe.parts.EmptyTestableItem
import com.willfp.eco.core.recipe.parts.GroupedTestableItems
import com.willfp.eco.core.recipe.workstation.VillagerRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.willfp.libreforge.ViolationContext
import com.willfp.libreforge.conditions.Conditions
import com.willfp.libreforge.effects.Effects
import com.willfp.libreforge.loader.LibreforgePlugin
import com.willfp.libreforge.loader.configs.ConfigCategory
import io.auxilor.ecocrafting.BuildConfig
import io.auxilor.ecocrafting.EcoCraftingPlugin
import io.auxilor.ecocrafting.category.integration.CategoryLoader
import io.auxilor.ecocrafting.recipe.model.EcoCraftingMeta
import io.auxilor.ecocrafting.recipe.model.IngredientMatcher
import io.auxilor.ecocrafting.recipe.model.RecipeDisplayType
import io.auxilor.ecocrafting.recipe.model.RecipeIngredient
import io.auxilor.ecocrafting.recipe.model.RecipeSymmetry
import io.auxilor.ecocrafting.recipe.service.RecipeService
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.AbstractVillager
import org.bukkit.inventory.ItemStack
import com.willfp.eco.core.recipe.workstation.SmeltingType as WSmeltingType

class RecipeLoader(
    internal val plugin: EcoCraftingPlugin,
    internal val recipeService: RecipeService,
    private val capEnforcer: RecipeCapEnforcer,
    private val categoryLoader: CategoryLoader
) : ConfigCategory("recipe", "recipes") {

    override fun clear(plugin: LibreforgePlugin) {
        WorkstationRecipes.clear()
        recipeService.clear()
        capEnforcer.clear()
    }

    override fun acceptConfig(plugin: LibreforgePlugin, id: String, config: Config) {
        if (config.has("enabled") && !config.getBool("enabled")) return
        val type = config.getString("type").lowercase()

        if (!capEnforcer.tryRegister(BuildConfig.FREE_VERSION, type, id)) {
            val cap = capEnforcer.capFor(type)
            plugin.logger.warning("The free version of EcoCrafting only supports $cap recipes for type '$type'.")
            plugin.logger.warning("Purchase the full version of EcoCrafting to remove this restriction!")
            return
        }

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
            capEnforcer.release(type, id)
            plugin.logger.warning("Failed to load recipe $id: ${e.message}")
        }
    }

    override fun afterReload(plugin: LibreforgePlugin) {
        if (plugin.configYml.getBool("villager-scan-on-reload")) scanVillagers()
        recipeService.allKeys().forEach { key ->
            val categoryId = recipeService.getMeta(key)?.categoryId ?: return@forEach
            val output = WorkstationRecipes.getAll().firstOrNull { it.key == key }?.output ?: return@forEach
            val category = categoryLoader.getByID(categoryId)
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
                val persistentDataContainer = villager.persistentDataContainer
                persistentDataContainer.keys.filter { it.namespace == "ecocrafting" && it.key.startsWith("vr_") }
                    .filter { it.key !in validKeyNames }
                    .forEach { persistentDataContainer.remove(it) }
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

    internal fun parsePrice(config: Config): ConfiguredPrice {
        val priceConfig = config.getSubsectionOrNull("price") ?: return ConfiguredPrice.FREE
        return ConfiguredPrice.createOrFree(priceConfig)
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
            categoryId = config.getStringOrNull("category")?.takeIf { it.isNotBlank() },
            price = parsePrice(config)
        )
    }

    internal fun registerWithMeta(recipe: WorkstationRecipe, meta: EcoCraftingMeta) {
        recipe.register()
        recipeService.register(recipe.key, meta)
    }

    internal fun generateSymmetryVariants(parts: List<RecipeIngredient>): List<Pair<String, List<RecipeIngredient>>> {
        val variants = mutableListOf<Pair<String, List<RecipeIngredient>>>()
        val seen = mutableSetOf<List<RecipeIngredient?>>()
        fun fingerprint(ingredients: List<RecipeIngredient>) = ingredients.map { if (it.empty) null else it }
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

}
