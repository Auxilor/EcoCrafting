package com.auxilor.ecocrafting.integration

import com.willfp.eco.core.items.CustomItem
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.TestableItem
import com.willfp.eco.core.items.provider.ItemProvider
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import com.auxilor.ecocrafting.EcoCraftingPlugin
import com.auxilor.ecocrafting.recipe.IngredientMatcher
import com.auxilor.ecocrafting.recipe.RecipeIngredient
import com.auxilor.ecocrafting.recipe.RecipeSource
import com.auxilor.ecocrafting.recipe.ResolvedRecipe

/**
 * Optional VaultPack integration.
 *
 * EcoCrafting does not compile against or ship VaultPack. VaultPack exposes a
 * tiny runtime service through Bukkit's ServicesManager, and this adapter uses
 * reflection to consume that service only when VaultPack is installed.
 */
object VaultPackIntegration {
    private const val SERVICE_CLASS_NAME = "com.vaultpack.api.VaultPackEcoCraftingService"

    private var pluginAvailable = false
    private var EcoCraftingService: Any? = null

    fun init(plugin: EcoCraftingPlugin) {
        pluginAvailable = Bukkit.getPluginManager().isPluginEnabled("VaultPack")
        if (!pluginAvailable) return

        runCatching {
            val serviceClass = Class.forName(SERVICE_CLASS_NAME)
            val registration = Bukkit.getServicesManager().getRegistrationUnchecked(serviceClass)
                ?: error("VaultPack EcoCrafting service is not registered")
            val service = registration.provider

            if (!(service.call("isAvailable") as? Boolean ?: false)) {
                error("VaultPack EcoCrafting service is not ready")
            }

            EcoCraftingService = service
            Items.registerItemProvider(VaultPackItemProvider())
            plugin.logger.info("[EcoCrafting] VaultPack integration enabled through Bukkit service")
        }.onFailure {
            plugin.logger.warning("[EcoCrafting] VaultPack integration disabled: ${it.message}")
            pluginAvailable = false
            EcoCraftingService = null
        }
    }

    fun isEnabled(): Boolean = pluginAvailable && EcoCraftingService != null

    fun hasBackpackType(id: String): Boolean {
        val service = EcoCraftingService ?: return false
        return service.call("hasBackpackType", id) as? Boolean ?: false
    }

    fun isBackpackItem(item: ItemStack, backpackId: String): Boolean {
        val service = EcoCraftingService ?: return false
        return service.call("isBackpackItem", item, backpackId) as? Boolean ?: false
    }

    fun createBackpackItem(id: String): ItemStack? {
        val service = EcoCraftingService ?: return null
        return (service.call("createBackpackItem", id) as? ItemStack)?.clone()
    }

    fun resolveRecipe(customItem: CustomItem): ResolvedRecipe? {
        if (customItem.key.namespace != "vaultpack") return null

        val service = EcoCraftingService ?: return null
        val backpackId = customItem.key.key
        if (!(service.call("hasRecipe", backpackId) as? Boolean ?: false)) return null

        val recipeStrings = service.call("getRecipe", backpackId) as? List<*> ?: return null
        if (recipeStrings.size != 9) return null

        val output = createBackpackItem(backpackId) ?: return null
        val ingredients = recipeStrings.map { parseRecipeItem(it?.toString().orEmpty()) }

        return ResolvedRecipe(
            key = customItem.key,
            output = output,
            ingredients = ingredients,
            source = RecipeSource.VAULTPACK
        )
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
        if (!VaultPackIntegration.hasBackpackType(key)) return null
        val item = VaultPackIntegration.createBackpackItem(key) ?: return null
        return VaultPackCustomItem(key, item)
    }
}

class VaultPackCustomItem(
    private val backpackId: String,
    item: ItemStack
) : CustomItem(
    NamespacedKey("vaultpack", backpackId),
    { stack -> VaultPackIntegration.isBackpackItem(stack, backpackId) },
    item.clone()
)

@Suppress("UNCHECKED_CAST")
private fun org.bukkit.plugin.ServicesManager.getRegistrationUnchecked(serviceClass: Class<*>): org.bukkit.plugin.RegisteredServiceProvider<Any>? {
    return getRegistration(serviceClass as Class<Any>)
}

private fun Any.call(name: String, vararg args: Any?): Any? {
    val method = javaClass.methods.firstOrNull { it.name == name && it.parameterCount == args.size } ?: return null
    return method.invoke(this, *args)
}
