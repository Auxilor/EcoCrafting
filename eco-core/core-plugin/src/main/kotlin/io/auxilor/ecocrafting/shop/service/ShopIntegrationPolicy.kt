package io.auxilor.ecocrafting.shop.service

// Decision logic for whether EcoShop integration may run. Kept separate from
// ShopIntegrationService so it's testable without a Bukkit plugin manager.
object ShopIntegrationPolicy {
    fun isIntegrationAllowed(freeVersion: Boolean): Boolean = !freeVersion

    fun shouldLogUpsell(freeVersion: Boolean, pluginAvailable: Boolean): Boolean =
        freeVersion && pluginAvailable
}
