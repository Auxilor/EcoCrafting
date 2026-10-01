package com.exanthiax.ecocrafting.recipegui.integration

import com.willfp.eco.core.items.Items
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.recipegui.service.PendingRecipe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RecipeEditSaveTest {

    @TempDir
    lateinit var dataFolder: File

    private val input: ItemStack = mockk { every { isEmpty } returns false }

    private val output: ItemStack = mockk { every { isEmpty } returns false }

    private fun pending(typeKey: String, id: String) = PendingRecipe(
        typeKey = typeKey,
        parts = if (typeKey == "stonecutter") mapOf(0 to input) else emptyMap(),
        output = output,
        shapeless = false,
        symmetry = false,
        supportCrafter = false,
        cookTime = null,
        experience = 0.0,
        profession = "",
        minLevel = 0,
        chance = 1.0,
        wanderingTrader = false,
        villagerXp = 0,
        id = id,
        permission = "",
        category = "",
        lockedByDefault = false,
        showWhenLocked = false,
        repairCost = 1,
        brewTime = null
    )

    private fun save(pending: PendingRecipe, existing: String): YamlConfiguration {
        val plugin = mockk<EcoCraftingPlugin> { every { this@mockk.dataFolder } returns this@RecipeEditSaveTest.dataFolder }
        File(dataFolder, "recipes").mkdirs()
        File(dataFolder, "recipes/${pending.id}.yml").writeText(existing.trimIndent())
        RecipeCreatorConfigWriter(plugin).saveRecipeYaml(pending)
        return YamlConfiguration.loadConfiguration(File(dataFolder, "recipes/${pending.id}.yml"))
    }

    @BeforeEach
    fun setUp() {
        mockkStatic(Items::class)
        every { Items.getCustomItem(any()) } returns null
        every { Items.toLookupString(input) } returns "stone"
        every { Items.toLookupString(output) } returns "netherite_sword"
    }

    @Test
    fun `editing a recipe keeps its price, effects and lore and applies the new builder values`() {
        val saved = save(
            pending("crafting_table", "epic_sword"),
            """
            enabled: true
            type: crafting_table
            output: diamond_sword
            permission: old.node
            lore:
              - "&7Hand written"
            # Costs coins.
            price:
              value: "100"
              type: coins
            effects:
              - id: give_xp
                args:
                  amount: 10
            crafting-conditions:
              - id: is_sneaking
            """
        )

        assertEquals("netherite_sword", saved.getString("output"))
        assertFalse(saved.contains("permission"))
        assertEquals(true, saved.getBoolean("enabled"))
        assertEquals(listOf("&7Hand written"), saved.getStringList("lore"))
        assertEquals("100", saved.getString("price.value"))
        assertEquals(listOf("Costs coins."), saved.getComments("price"))
        assertEquals("give_xp", saved.getMapList("effects").first()["id"])
        assertEquals("is_sneaking", saved.getMapList("crafting-conditions").first()["id"])
        assertEquals(emptyList<Any>(), saved.getList("unlock-conditions"))
    }

    @Test
    fun `editing a stonecutter keeps every output and its keys`() {
        val saved = save(
            pending("stonecutter", "stone_cuts"),
            """
            type: stonecutter
            input: cobblestone
            outputs:
              - item: stone_slab
                price:
                  value: "5"
                  type: coins
                effects:
                  - id: give_xp
                crafting-conditions:
                  - id: is_sneaking
              - item: stone_stairs
                price:
                  value: "9"
                  type: coins
                effects:
                  - id: give_money
                crafting-conditions:
                  - id: is_gliding
              - item: stone_bricks
                price:
                  value: "3"
                  type: coins
                effects:
                  - id: send_message
                crafting-conditions:
                  - id: is_night
            """
        )

        val outputs = saved.getMapList("outputs")
        assertEquals("stone", saved.getString("input"))
        assertEquals(listOf("netherite_sword", "stone_stairs", "stone_bricks"), outputs.map { it["item"] })
        assertEquals(listOf("5", "9", "3"), outputs.map { (it["price"] as Map<*, *>)["value"] })
        assertEquals(listOf("give_xp", "give_money", "send_message"), outputs.map { (it["effects"] as List<*>).single().let { effect -> (effect as Map<*, *>)["id"] } })
        assertEquals(listOf("is_sneaking", "is_gliding", "is_night"), outputs.map { (it["crafting-conditions"] as List<*>).single().let { condition -> (condition as Map<*, *>)["id"] } })
    }
}
