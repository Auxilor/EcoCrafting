package com.exanthiax.ecocrafting.unlock.integration

import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.recipe.model.EcoCraftingMeta
import com.exanthiax.ecocrafting.recipe.model.RecipeDisplayType
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import com.willfp.libreforge.conditions.ConditionBlock
import com.willfp.libreforge.conditions.ConditionList
import com.willfp.libreforge.effects.EffectList
import com.willfp.libreforge.toDispatcher
import io.mockk.every
import io.mockk.mockk
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.logging.Logger

class RecipeUnlockHolderProviderTest {

    private val player: Player = mockk(relaxed = true)
    private val plugin = mockk<EcoCraftingPlugin>(relaxed = true)
    private val unlockService = mockk<RecipeUnlockService>()
    private val locked = mutableSetOf<NamespacedKey>()
    private val manuallyLocked = mutableSetOf<NamespacedKey>()
    private var live: Boolean? = null

    private lateinit var recipeService: RecipeService
    private lateinit var provider: RecipeUnlockHolderProvider

    private fun meta(unlockConditions: ConditionList) = EcoCraftingMeta(
        effectsChain = null,
        visibilityConditions = ConditionList(emptyList()),
        craftingConditions = ConditionList(emptyList()),
        lockedByDefault = true,
        showWhenLocked = false,
        lockedLore = emptyList(),
        unlockConditions = unlockConditions,
        displayType = RecipeDisplayType.CRAFTING
    )

    private fun metConditions() = ConditionList(listOf(mockk<ConditionBlock<*>>()))

    private fun key(id: String) = NamespacedKey("ecocrafting", id)

    private fun providedKeys() = provider.provide(player.toDispatcher())
        .map { (it.holder as RecipeUnlockHolder).recipeKey }
        .toSet()

    @BeforeEach
    fun setUp() {
        every { plugin.logger } returns Logger.getLogger("RecipeUnlockHolderProviderTest")
        every { plugin.configYml.getBoolOrNull("unlock-conditions.live") } answers { live }
        every { unlockService.isLocked(player, any(), any()) } answers { secondArg<NamespacedKey>() in locked }
        every { unlockService.isManuallyLocked(player, any()) } answers { secondArg<NamespacedKey>() in manuallyLocked }
        recipeService = RecipeService(plugin)
        provider = RecipeUnlockHolderProvider(plugin, recipeService, unlockService) { EffectList(emptyList()) }
    }

    @Test
    fun `provides a holder for each locked recipe with unlock conditions`() {
        recipeService.register(key("first"), meta(metConditions()))
        recipeService.register(key("second"), meta(metConditions()))
        recipeService.register(key("manual_only"), meta(ConditionList(emptyList())))
        locked += listOf(key("first"), key("second"), key("manual_only"))

        assertEquals(setOf(key("first"), key("second")), providedKeys())
    }

    @Test
    fun `skips unlocked and manually locked recipes`() {
        recipeService.register(key("unlocked"), meta(metConditions()))
        recipeService.register(key("manual"), meta(metConditions()))
        locked += key("manual")
        manuallyLocked += key("manual")

        assertTrue(providedKeys().isEmpty())
    }

    @Test
    fun `provides nothing when live checks are turned off`() {
        live = false
        recipeService.register(key("first"), meta(metConditions()))
        locked += key("first")

        assertTrue(providedKeys().isEmpty())
    }

    @Test
    fun `provides nothing to non-player dispatchers`() {
        recipeService.register(key("first"), meta(metConditions()))
        locked += key("first")

        assertTrue(provider.provide(mockk<Block>(relaxed = true).toDispatcher()).isEmpty())
    }

    @Test
    fun `holder carries the recipe unlock conditions and is reused until the recipe reloads`() {
        val conditions = metConditions()
        recipeService.register(key("first"), meta(conditions))
        locked += key("first")

        val holder = provider.provide(player.toDispatcher()).single().holder
        assertSame(conditions, holder.conditions)
        assertSame(holder, provider.provide(player.toDispatcher()).single().holder)

        recipeService.register(key("first"), meta(metConditions()))
        assertTrue(holder !== provider.provide(player.toDispatcher()).single().holder)
    }
}
