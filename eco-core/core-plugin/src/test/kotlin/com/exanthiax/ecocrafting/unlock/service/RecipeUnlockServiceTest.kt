package com.exanthiax.ecocrafting.unlock.service

import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.core.persistence.PlayerDataKeys
import com.exanthiax.ecocrafting.libreforge.TriggerRecipeUnlocked
import com.exanthiax.ecocrafting.recipe.model.EcoCraftingMeta
import com.exanthiax.ecocrafting.recipe.model.RecipeDisplayType
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.willfp.libreforge.LibreforgeSpigotPlugin
import com.willfp.libreforge.conditions.ConditionBlock
import com.willfp.libreforge.conditions.ConditionList
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.logging.Logger

class RecipeUnlockServiceTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun seedLibreforgePlugin() {
            Class.forName("com.willfp.libreforge.LibreforgeSpigotPluginKt").getDeclaredField("plugin").apply {
                isAccessible = true
                set(null, mockk<LibreforgeSpigotPlugin>(relaxed = true))
            }
        }
    }

    private val player: Player = mockk(relaxed = true)
    private val key = NamespacedKey("ecocrafting", "example")

    private lateinit var recipeService: RecipeService
    private lateinit var unlockService: RecipeUnlockService
    private val unlocked = mutableSetOf<NamespacedKey>()
    private val manuallyLocked = mutableSetOf<NamespacedKey>()

    private fun conditions(vararg met: Boolean) = ConditionList(
        met.map { result ->
            mockk<ConditionBlock<*>> { every { isMet(any(), any()) } returns result }
        }
    )

    private fun meta(lockedByDefault: Boolean = true, unlockConditions: ConditionList = conditions()) = EcoCraftingMeta(
        effectsChain = null,
        visibilityConditions = conditions(),
        craftingConditions = conditions(),
        lockedByDefault = lockedByDefault,
        showWhenLocked = false,
        lockedLore = emptyList(),
        unlockConditions = unlockConditions,
        displayType = RecipeDisplayType.CRAFTING
    )

    @BeforeEach
    fun setUp() {
        val plugin = mockk<EcoCraftingPlugin>(relaxed = true)
        every { plugin.logger } returns Logger.getLogger("RecipeUnlockServiceTest")
        recipeService = RecipeService(plugin)
        unlockService = spyk(RecipeUnlockService(mockk<PlayerDataKeys>(relaxed = true), recipeService))
        every { unlockService.isLocked(player, any(), any()) } answers { secondArg<NamespacedKey>() !in unlocked }
        every { unlockService.isManuallyLocked(player, any()) } answers { secondArg<NamespacedKey>() in manuallyLocked }
        every { unlockService.unlock(player, any(), any()) } answers { unlocked += secondArg<NamespacedKey>() }
        mockkObject(TriggerRecipeUnlocked)
        every { TriggerRecipeUnlocked.dispatch(any(), any(), any(), any()) } just runs
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(TriggerRecipeUnlocked)
    }

    @Test
    fun `empty unlock conditions never auto-unlock`() {
        recipeService.register(key, meta())

        unlockService.checkAutoUnlocks(player)

        assertFalse(unlockService.checkAutoUnlock(player, key, meta()))
        assertTrue(unlocked.isEmpty())
        verify(exactly = 0) { TriggerRecipeUnlocked.dispatch(any(), any(), any(), any()) }
    }

    @Test
    fun `met unlock conditions unlock and fire recipe_unlocked once`() {
        recipeService.register(key, meta(unlockConditions = conditions(true)))

        unlockService.checkAutoUnlocks(player)
        unlockService.checkAutoUnlocks(player)

        assertEquals(setOf(key), unlocked)
        verify(exactly = 1) { TriggerRecipeUnlocked.dispatch(any(), match { it.text == key.toString() }, any(), any()) }
    }

    @Test
    fun `already unlocked recipe does not fire again`() {
        unlocked += key
        val recipeMeta = meta(unlockConditions = conditions(true))
        recipeService.register(key, recipeMeta)

        assertFalse(unlockService.checkAutoUnlock(player, key, recipeMeta))
        verify(exactly = 0) { unlockService.unlock(player, any(), any()) }
        verify(exactly = 0) { TriggerRecipeUnlocked.dispatch(any(), any(), any(), any()) }
    }

    @Test
    fun `unmet unlock conditions stay locked`() {
        val recipeMeta = meta(unlockConditions = conditions(true, false))
        recipeService.register(key, recipeMeta)

        unlockService.checkAutoUnlocks(player)

        assertFalse(unlockService.checkAutoUnlock(player, key, recipeMeta))
        assertTrue(unlocked.isEmpty())
        verify(exactly = 0) { TriggerRecipeUnlocked.dispatch(any(), any(), any(), any()) }
    }

    @Test
    fun `manually locked recipe is not auto-unlocked`() {
        manuallyLocked += key
        val recipeMeta = meta(unlockConditions = conditions(true))
        recipeService.register(key, recipeMeta)

        unlockService.checkAutoUnlocks(player)

        assertFalse(unlockService.checkAutoUnlock(player, key, recipeMeta))
        assertTrue(unlocked.isEmpty())
        verify(exactly = 0) { TriggerRecipeUnlocked.dispatch(any(), any(), any(), any()) }
    }

    @Test
    fun `only locked recipes with unlock conditions are auto-unlock candidates`() {
        val candidate = NamespacedKey("ecocrafting", "candidate")
        recipeService.register(candidate, meta(unlockConditions = conditions(true)))
        recipeService.register(NamespacedKey("ecocrafting", "manual"), meta())
        recipeService.register(NamespacedKey("ecocrafting", "open"), meta(lockedByDefault = false, unlockConditions = conditions(true)))

        assertEquals(setOf(candidate), recipeService.autoUnlockKeys())

        recipeService.clear()
        assertTrue(recipeService.autoUnlockKeys().isEmpty())
    }
}
