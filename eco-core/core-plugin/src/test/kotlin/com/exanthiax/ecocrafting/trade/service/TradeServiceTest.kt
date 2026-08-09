package com.exanthiax.ecocrafting.trade.service

import com.exanthiax.ecocrafting.commands.parseTradeIds
import com.exanthiax.ecocrafting.crafting.service.hasRecipePermission
import com.willfp.eco.core.recipe.workstation.WorkstationRecipe
import io.mockk.every
import io.mockk.mockk
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class TradeServiceTest {

    private fun recipe(id: String, permission: String? = null): WorkstationRecipe =
        mockk<WorkstationRecipe>().also {
            every { it.key } returns NamespacedKey("ecocrafting", id)
            every { it.permission } returns permission
        }

    private fun player(vararg granted: String): Player =
        mockk<Player>().also { player ->
            every { player.uniqueId } returns UUID.randomUUID()
            every { player.hasPermission(any<String>()) } answers { firstArg<String>() in granted }
        }

    @Test
    fun `trade ids are split, trimmed, and blanks dropped`() {
        assertEquals(listOf("one", "two", "three"), parseTradeIds(" one, two ,three "))
        assertEquals(listOf("one"), parseTradeIds("one"))
        // Duplicates are kept - listing the same trade twice is intentional.
        assertEquals(listOf("one", "one"), parseTradeIds("one,one"))
        assertEquals(emptyList<String>(), parseTradeIds(",, ,"))
        assertEquals(emptyList<String>(), parseTradeIds(null))
    }

    @Test
    fun `max uses of zero means unlimited`() {
        assertEquals(Int.MAX_VALUE, resolveMaxUses(0))
        assertEquals(Int.MAX_VALUE, resolveMaxUses(-1))
        assertEquals(3, resolveMaxUses(3))
    }

    @Test
    fun `implicit per-recipe permission grants access`() {
        val trade = recipe("emerald_book")
        assertTrue(hasRecipePermission(player("ecocrafting.recipe.emerald_book"), trade))
        assertTrue(hasRecipePermission(player("ecocrafting.recipe.*"), trade))
        assertFalse(hasRecipePermission(player(), trade))
    }

    @Test
    fun `explicit permission replaces the implicit node`() {
        val trade = recipe("emerald_book", permission = "myserver.trade")
        assertTrue(hasRecipePermission(player("myserver.trade"), trade))
        // The implicit node no longer applies once the recipe names its own permission.
        assertFalse(hasRecipePermission(player("ecocrafting.recipe.emerald_book"), trade))
    }

    @Test
    fun `session resolves merchant slots back to recipe keys`() {
        val service = TradeSessionService()
        val player = player()
        val first = NamespacedKey("ecocrafting", "first")
        val second = NamespacedKey("ecocrafting", "second")

        service.open(player, listOf(first, second))

        assertEquals(first, service.keyAt(player, 0))
        assertEquals(second, service.keyAt(player, 1))
        // Out-of-range and unselected (-1) indices fall through to ingredient matching.
        assertNull(service.keyAt(player, 2))
        assertNull(service.keyAt(player, -1))
    }

    @Test
    fun `closing the session stops resolving slots`() {
        val service = TradeSessionService()
        val player = player()
        service.open(player, listOf(NamespacedKey("ecocrafting", "first")))

        service.close(player)

        assertNull(service.keyAt(player, 0))
    }

    @Test
    fun `sessions are per player`() {
        val service = TradeSessionService()
        val opened = player()
        val other = player()
        service.open(opened, listOf(NamespacedKey("ecocrafting", "first")))

        assertNull(service.keyAt(other, 0))
    }
}
