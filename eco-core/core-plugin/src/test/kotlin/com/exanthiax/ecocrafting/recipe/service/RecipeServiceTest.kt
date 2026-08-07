package com.exanthiax.ecocrafting.recipe.service

import com.willfp.libreforge.conditions.ConditionList
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.recipe.model.EcoCraftingMeta
import com.exanthiax.ecocrafting.recipe.model.RecipeDisplayType
import io.mockk.every
import io.mockk.mockk
import org.bukkit.NamespacedKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.logging.Logger

class RecipeServiceTest {

    private lateinit var service: RecipeService

    private fun meta(displayType: RecipeDisplayType = RecipeDisplayType.CRAFTING) = EcoCraftingMeta(
        giveResultItem = true,
        effectsChain = null,
        visibilityConditions = ConditionList(emptyList()),
        craftingConditions = ConditionList(emptyList()),
        lockedByDefault = false,
        showWhenLocked = false,
        lockedLore = emptyList(),
        unlockConditions = ConditionList(emptyList()),
        displayType = displayType
    )

    @BeforeEach
    fun setUp() {
        val plugin = mockk<EcoCraftingPlugin>(relaxed = true)
        every { plugin.logger } returns Logger.getLogger("RecipeServiceTest")
        service = RecipeService(plugin)
    }

    @Test
    fun `registered recipe metadata is retrievable by key`() {
        val key = NamespacedKey("ecocrafting", "example")
        service.register(key, meta())

        assertEquals(RecipeDisplayType.CRAFTING, service.getMeta(key)?.displayType)
        assertEquals(setOf(key), service.allKeys())
    }

    @Test
    fun `unknown key has no metadata`() {
        assertNull(service.getMeta(NamespacedKey("ecocrafting", "missing")))
    }

    @Test
    fun `symmetry variant keys resolve back to their base key`() {
        val base = NamespacedKey("ecocrafting", "example")
        val variant = NamespacedKey("ecocrafting", "example_rot90")
        service.registerVariant(variant, base)

        assertEquals(base, service.baseKeyForVariant(variant))
        // A key with no registered variant mapping resolves to itself.
        assertEquals(base, service.baseKeyForVariant(base))
    }

    @Test
    fun `resolveBaseKey strips eco suffixes and maps variants back to base`() {
        val base = NamespacedKey("ecocrafting", "example")
        val variant = NamespacedKey("ecocrafting", "example_rot90")
        service.registerVariant(variant, base)

        // A plain base key resolves to itself.
        assertEquals(base, service.resolveBaseKey(base))
        // eco `_displayed` / `_crafter` ghosts of the base resolve to the base.
        assertEquals(base, service.resolveBaseKey(NamespacedKey("ecocrafting", "example_displayed")))
        assertEquals(base, service.resolveBaseKey(NamespacedKey("ecocrafting", "example_crafter")))
        // A symmetry variant - bare or with an eco suffix stacked on top - resolves to the base.
        assertEquals(base, service.resolveBaseKey(variant))
        assertEquals(base, service.resolveBaseKey(NamespacedKey("ecocrafting", "example_rot90_displayed")))
        assertEquals(base, service.resolveBaseKey(NamespacedKey("ecocrafting", "example_rot90_crafter")))
        // An unrelated (vanilla/other-plugin) recipe key is left untouched.
        val vanilla = NamespacedKey("minecraft", "diamond_sword")
        assertEquals(vanilla, service.resolveBaseKey(vanilla))
    }

    @Test
    fun `clear wipes both metadata and variant mappings`() {
        val base = NamespacedKey("ecocrafting", "example")
        val variant = NamespacedKey("ecocrafting", "example_rot90")
        service.register(base, meta())
        service.registerVariant(variant, base)

        service.clear()

        assertNull(service.getMeta(base))
        assertEquals(variant, service.baseKeyForVariant(variant))
    }

    @Test
    fun `keyOrWarn lowercases valid ids and rejects invalid ones`() {
        assertEquals(NamespacedKey("ecocrafting", "example"), service.keyOrWarn("Example"))
        assertNull(service.keyOrWarn("not a valid id!"))
    }
}
