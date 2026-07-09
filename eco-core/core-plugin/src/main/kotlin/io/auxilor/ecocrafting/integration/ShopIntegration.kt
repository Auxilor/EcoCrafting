package io.auxilor.ecocrafting.integration

import com.willfp.ecoshop.shop.BuyStatus
import com.willfp.ecoshop.shop.BuyType
import com.willfp.ecoshop.shop.ShopItem
import com.willfp.ecoshop.shop.shopItem
import io.auxilor.ecocrafting.BuildConfig
import io.auxilor.ecocrafting.EcoCraftingPlugin
import io.auxilor.ecocrafting.plugin
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

// Optional EcoShop integration boundary. Keep all direct EcoShop calls here so
// weekly EcoShop API changes are isolated.
object ShopIntegration {
    private var pluginAvailable = false
    private var configEnabled = false
    private var showPrices = true
    private var autoBuy = false
    private var requireShiftClick = true

    fun init(plugin: EcoCraftingPlugin) {
        pluginAvailable = Bukkit.getPluginManager().isPluginEnabled("EcoShop")

        if (ShopIntegrationPolicy.shouldLogUpsell(BuildConfig.FREE_VERSION, pluginAvailable)) {
            plugin.logger.warning("EcoShop was found, but shop integration is a premium-only feature.")
            plugin.logger.warning("Purchase the full version of EcoCrafting to enable it!")
        }

        if (!ShopIntegrationPolicy.isIntegrationAllowed(BuildConfig.FREE_VERSION)) {
            configEnabled = false
            return
        }

        configEnabled = plugin.configYml.getBool("shop-integration.enabled")
        showPrices = plugin.configYml.getBool("shop-integration.show-prices")
        autoBuy = plugin.configYml.getBool("shop-integration.auto-buy-missing-materials")
        requireShiftClick = plugin.configYml.getBool("shop-integration.require-shift-click")

        when {
            pluginAvailable && configEnabled -> plugin.logger.info("EcoShop integration enabled")
            pluginAvailable -> plugin.logger.info("EcoShop found but integration disabled in config")
        }
    }

    fun isEnabled(): Boolean = pluginAvailable && configEnabled

    fun shouldShowPrices(): Boolean = isEnabled() && showPrices

    fun isAutoBuyEnabled(): Boolean = isEnabled() && autoBuy

    fun canAutoBuy(shiftClick: Boolean): Boolean {
        return isAutoBuyEnabled() && (!requireShiftClick || shiftClick)
    }

    fun getMaterialShopInfo(player: Player, material: ItemStack, amountNeeded: Int): MaterialShopInfo? {
        if (!isEnabled() || amountNeeded <= 0) return null
        val sampleItem = material.clone().apply { amount = 1 }
        val shopItem = sampleItem.shopItem ?: return null
        if (!shopItem.isBuyable) return null
        val purchaseTimes = shopItem.getPurchaseTimesFor(amountNeeded)
        val status = shopItem.getBuyStatus(player, purchaseTimes, BuyType.NORMAL)
        val price = shopItem.buyPrice ?: return null
        return MaterialShopInfo(
            amountNeeded = amountNeeded,
            canAfford = status == BuyStatus.ALLOW,
            canBuy = status == BuyStatus.ALLOW,
            status = status.name,
            priceDisplay = price.getDisplay(player, purchaseTimes * shopItem.getEffectiveBuyMultiplier(BuyType.NORMAL, player))
        )
    }

    fun purchaseMaterials(player: Player, materials: List<Pair<ItemStack, Int>>): PurchaseResult {
        if (!isEnabled() || !autoBuy) {
            return PurchaseResult(false, plugin.langYml.getString("messages.failed-reason.shop-auto-buy-disabled"))
        }

        val purchases = mutableListOf<Triple<ShopItem, Int, String>>()
        val unavailable = mutableListOf<String>()
        val unaffordable = mutableListOf<String>()

        for ((material, amount) in materials) {
            if (amount <= 0) continue
            val sampleItem = material.clone().apply { this.amount = 1 }
            val shopItem = sampleItem.shopItem
            if (shopItem == null || !shopItem.isBuyable) {
                unavailable += material.type.name
                continue
            }
            when (val status = shopItem.getBuyStatus(player, shopItem.getPurchaseTimesFor(amount), BuyType.NORMAL)) {
                BuyStatus.ALLOW -> purchases += Triple(shopItem, shopItem.getPurchaseTimesFor(amount), material.type.name)
                BuyStatus.CANNOT_AFFORD -> unaffordable += "${material.type.name} x$amount"
                else -> unavailable += "${material.type.name} (${status.name})"
            }
        }

        if (unavailable.isNotEmpty()) return PurchaseResult(false, plugin.langYml.getFormattedString("messages.failed-reason.shop-unavailable")
            .replace("%items%", unavailable.joinToString(", ")))
        if (unaffordable.isNotEmpty()) return PurchaseResult(false, plugin.langYml.getFormattedString("messages.failed-reason.shop-cannot-afford")
            .replace("%items%", unaffordable.joinToString(", ")))

        // EcoShop's buy() has no transactional rollback, so buy each item
        // independently and report which succeeded.
        val purchased = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for ((shopItem, purchaseTimes, name) in purchases) {
            try {
                shopItem.buy(player, purchaseTimes, BuyType.NORMAL)
                purchased += name
            } catch (e: Exception) {
                failed += name
                plugin.logger.warning("Failed to buy '$name' for ${player.name}: ${e.message}")
            }
        }

        return when {
            failed.isEmpty() -> PurchaseResult(true, "", purchased = purchased)
            purchased.isEmpty() -> PurchaseResult(false, plugin.langYml.getFormattedString("messages.failed-reason.shop-purchase-failed")
                .replace("%items%", failed.joinToString(", ")))
            else -> PurchaseResult(
                false,
                plugin.langYml.getFormattedString("messages.failed-reason.shop-purchase-partial")
                    .replace("%purchased%", purchased.joinToString(", "))
                    .replace("%failed%", failed.joinToString(", ")),
                partial = true,
                purchased = purchased
            )
        }
    }

    private fun ShopItem.getPurchaseTimesFor(amountNeeded: Int): Int {
        val buyAmount = this.buyAmount.coerceAtLeast(1)
        return ((amountNeeded + buyAmount - 1) / buyAmount).coerceAtLeast(1)
    }
}

data class MaterialShopInfo(
    val amountNeeded: Int,
    val canAfford: Boolean,
    val canBuy: Boolean,
    val status: String,
    val priceDisplay: String
)

data class PurchaseResult(
    val success: Boolean,
    val message: String,
    val partial: Boolean = false,
    val purchased: List<String> = emptyList()
)
