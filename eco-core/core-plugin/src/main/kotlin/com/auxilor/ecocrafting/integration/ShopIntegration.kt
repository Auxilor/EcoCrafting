package com.auxilor.ecocrafting.integration

import com.willfp.ecoshop.shop.BuyStatus
import com.willfp.ecoshop.shop.BuyType
import com.willfp.ecoshop.shop.getDisplay
import com.willfp.ecoshop.shop.shopItem
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import com.auxilor.ecocrafting.EcoCraftingPlugin
import com.auxilor.ecocrafting.ecoCraftingPlugin

/**
 * Optional EcoShop integration boundary.
 * Keep all direct EcoShop calls here so weekly EcoShop API changes are isolated.
 */
object ShopIntegration {
    private var pluginAvailable = false
    private var configEnabled = false
    private var showPrices = true
    private var autoBuy = false
    private var requireShiftClick = true

    fun init(plugin: EcoCraftingPlugin) {
        pluginAvailable = Bukkit.getPluginManager().isPluginEnabled("EcoShop")
        configEnabled = plugin.configYml.getBool("shop-integration.enabled")
        showPrices = plugin.configYml.getBool("shop-integration.show-prices")
        autoBuy = plugin.configYml.getBool("shop-integration.auto-buy-missing-materials")
        requireShiftClick = plugin.configYml.getBool("shop-integration.require-shift-click")

        when {
            pluginAvailable && configEnabled -> plugin.logger.info("[EcoCrafting] EcoShop integration enabled")
            pluginAvailable -> plugin.logger.info("[EcoCrafting] EcoShop found but integration disabled in config")
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
            return PurchaseResult(false, "EcoShop auto-purchase is disabled")
        }

        val purchases = mutableListOf<Pair<com.willfp.ecoshop.shop.ShopItem, Int>>()
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
                BuyStatus.ALLOW -> purchases += shopItem to shopItem.getPurchaseTimesFor(amount)
                BuyStatus.CANNOT_AFFORD -> unaffordable += "${material.type.name} x$amount"
                else -> unavailable += "${material.type.name} (${status.name})"
            }
        }

        if (unavailable.isNotEmpty()) return PurchaseResult(false, "Unavailable: ${unavailable.joinToString(", ")}")
        if (unaffordable.isNotEmpty()) return PurchaseResult(false, "Cannot afford: ${unaffordable.joinToString(", ")}")

        return runCatching {
            for ((shopItem, purchaseTimes) in purchases) {
                shopItem.buy(player, purchaseTimes, BuyType.NORMAL)
            }
            PurchaseResult(true, "Purchased missing materials")
        }.getOrElse { PurchaseResult(false, it.message ?: "Purchase failed") }
    }

    private fun com.willfp.ecoshop.shop.ShopItem.getPurchaseTimesFor(amountNeeded: Int): Int {
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
    val message: String
)
