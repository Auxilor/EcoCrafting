package io.auxilor.ecocrafting.integration

// Decision logic for whether EcoShop integration may run. Kept separate from
// ShopIntegration so it's testable without a Bukkit plugin manager.
object ShopIntegrationPolicy {
    fun isIntegrationAllowed(freeVersion: Boolean): Boolean = !freeVersion

    fun shouldLogUpsell(freeVersion: Boolean, pluginAvailable: Boolean): Boolean =
        freeVersion && pluginAvailable
}
