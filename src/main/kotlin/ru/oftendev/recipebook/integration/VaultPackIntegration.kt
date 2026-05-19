package ru.oftendev.recipebook.integration

import com.willfp.eco.core.items.CustomItem
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.TestableItem
import com.willfp.eco.core.items.provider.ItemProvider
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType
import ru.oftendev.recipebook.RecipeBookPlugin
import ru.oftendev.recipebook.recipe.IngredientMatcher
import ru.oftendev.recipebook.recipe.RecipeIngredient
import ru.oftendev.recipebook.recipe.RecipeSource
import ru.oftendev.recipebook.recipe.ResolvedRecipe
import java.net.URL
import java.util.Base64
import java.util.UUID

/**
 * Optional VaultPack integration.
 *
 * Kept reflection-based so RecipeBook can still boot if VaultPack is absent or updated.
 */
object VaultPackIntegration {
    private var pluginAvailable = false
    private var vaultPackPlugin: Any? = null
    private var backpackTypeManager: Any? = null

    fun init(plugin: RecipeBookPlugin) {
        pluginAvailable = Bukkit.getPluginManager().isPluginEnabled("VaultPack")
        if (!pluginAvailable) return

        runCatching {
            val pluginClass = Class.forName("com.vaultpack.VaultPackPlugin")
            vaultPackPlugin = pluginClass.getMethod("getInstance").invoke(null)
            backpackTypeManager = vaultPackPlugin?.call("getBackpackTypeManager")
                ?: vaultPackPlugin?.getProperty("backpackTypeManager")
            Items.registerItemProvider(VaultPackItemProvider())
            plugin.logger.info("[RecipeBook] VaultPack integration enabled")
        }.onFailure {
            plugin.logger.warning("[RecipeBook] VaultPack integration disabled: ${it.message}")
            pluginAvailable = false
            vaultPackPlugin = null
            backpackTypeManager = null
        }
    }

    fun isEnabled(): Boolean = pluginAvailable

    fun resolveRecipe(customItem: CustomItem): ResolvedRecipe? {
        if (customItem.key.namespace != "vaultpack") return null
        val backpackType = getBackpackType(customItem.key.key) ?: return null
        if (!(backpackType.call("hasRecipe") as? Boolean ?: false)) return null

        val recipeStrings = backpackType.getProperty("recipe") as? List<*> ?: return null
        if (recipeStrings.size != 9) return null

        val ingredients = recipeStrings.map { parseRecipeItem(it?.toString().orEmpty()) }
        return ResolvedRecipe(
            key = customItem.key,
            output = Items.lookup("vaultpack:${customItem.key.key}").item,
            ingredients = ingredients,
            source = RecipeSource.VAULTPACK
        )
    }

    fun getBackpackType(id: String): Any? {
        val manager = backpackTypeManager ?: return null
        return manager.call("getBackpackType", id)
    }

    private fun parseRecipeItem(recipeString: String): RecipeIngredient {
        if (recipeString.isBlank()) {
            return RecipeIngredient.empty(ItemStack(Material.AIR))
        }

        val parts = recipeString.trim().split(Regex("\\s+"))
        val materialName = parts[0]
        val amount = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val stack = Material.matchMaterial(materialName)?.let { ItemStack(it, amount) }
            ?: runCatching { Items.lookup(materialName).item.apply { this.amount = amount } }.getOrDefault(ItemStack(Material.AIR))

        return RecipeIngredient(stack, IngredientMatcher.SimilarItem(stack))
    }
}

class VaultPackItemProvider : ItemProvider("vaultpack") {
    override fun provideForKey(key: String): TestableItem? {
        val backpackType = VaultPackIntegration.getBackpackType(key) ?: return null
        return VaultPackCustomItem(key, backpackType)
    }
}

class VaultPackCustomItem(
    private val backpackId: String,
    private val backpackType: Any
) : CustomItem(
    NamespacedKey("vaultpack", backpackId),
    { item -> isVaultPackItem(item, backpackId) },
    createBackpackItemStack(backpackType, backpackId)
) {
    companion object {
        private fun isVaultPackItem(item: ItemStack, backpackId: String): Boolean {
            val meta = item.itemMeta ?: return false
            val key = NamespacedKey("vaultpack", "backpack_type")
            return meta.persistentDataContainer.get(key, PersistentDataType.STRING) == backpackId
        }

        private fun createBackpackItemStack(type: Any, typeId: String): ItemStack {
            val material = type.getProperty("material") as? Material ?: Material.CHEST
            val item = ItemStack(material, 1)
            val meta = item.itemMeta ?: return item

            if (material == Material.PLAYER_HEAD && (type.call("hasTexture") as? Boolean ?: false)) {
                val texture = type.getProperty("texture") as? String
                if (texture != null && meta is SkullMeta) {
                    applyTexture(meta, texture)
                }
            }

            val serializer = LegacyComponentSerializer.legacyAmpersand()
            (type.getProperty("displayName") as? String)?.let { meta.displayName(serializer.deserialize(it)) }

            val defaultTier = type.getProperty("defaultTier")
            val tierName = defaultTier?.getProperty("displayName")?.toString().orEmpty()
            val tierSize = defaultTier?.getProperty("size")?.toString().orEmpty()
            val lore = (type.getProperty("lore") as? List<*>)?.map {
                serializer.deserialize(
                    it.toString()
                        .replace("%tier%", tierName)
                        .replace("%size%", tierSize)
                        .replace("%used%", "0")
                )
            }
            if (lore != null) meta.lore(lore)

            val customModelData = type.getProperty("customModelData") as? Int ?: 0
            if (customModelData > 0) {
                @Suppress("DEPRECATION")
                meta.setCustomModelData(customModelData)
            }

            if (type.call("hasGlow") as? Boolean ?: false) {
                meta.addEnchant(Enchantment.LURE, 1, true)
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            }

            meta.persistentDataContainer.set(NamespacedKey("vaultpack", "backpack_type"), PersistentDataType.STRING, typeId)
            item.itemMeta = meta
            return item
        }

        private fun applyTexture(skullMeta: SkullMeta, texture: String) {
            runCatching {
                val profile = Bukkit.createPlayerProfile(UUID.randomUUID())
                val textures = profile.textures
                val decoded = String(Base64.getDecoder().decode(texture))
                val urlStart = decoded.indexOf("\"url\":\"") + 7
                val urlEnd = decoded.indexOf("\"", urlStart)
                if (urlStart > 6 && urlEnd > urlStart) {
                    textures.skin = URL(decoded.substring(urlStart, urlEnd))
                    profile.setTextures(textures)
                    skullMeta.setOwnerProfile(profile)
                }
            }
        }
    }
}

private fun Any.call(name: String, vararg args: Any?): Any? {
    val method = javaClass.methods.firstOrNull { it.name == name && it.parameterCount == args.size } ?: return null
    return method.invoke(this, *args)
}

private fun Any.getProperty(name: String): Any? {
    val capitalized = name.replaceFirstChar { it.uppercase() }
    return call("get$capitalized") ?: runCatching {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.get(this)
    }.getOrNull()
}
