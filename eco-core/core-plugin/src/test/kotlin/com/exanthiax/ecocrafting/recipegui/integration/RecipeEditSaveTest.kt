package com.exanthiax.ecocrafting.recipegui.integration

import com.willfp.eco.core.items.Items
import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.limit.model.UNLIMITED
import com.exanthiax.ecocrafting.recipegui.service.PendingRecipe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RecipeEditSaveTest {

    @TempDir
    lateinit var dataFolder: File

    private val output: ItemStack = mockk { every { isEmpty } returns false }

    private fun pending() = PendingRecipe(
        typeKey = "crafting_table",
        parts = emptyMap(),
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
        id = "epic_sword",
        permission = "",
        category = "",
        lockedByDefault = false,
        showWhenLocked = false,
        repairCost = 1,
        brewTime = null,
        playerCraftLimit = 4,
        globalCraftLimit = UNLIMITED
    )

    @Test
    fun `editing a recipe keeps its price, effects and lore and applies the new builder values`() {
        mockkStatic(Items::class)
        every { Items.getCustomItem(output) } returns null
        every { Items.toLookupString(output) } returns "netherite_sword"
        val plugin = mockk<EcoCraftingPlugin> { every { this@mockk.dataFolder } returns this@RecipeEditSaveTest.dataFolder }
        File(dataFolder, "recipes").mkdirs()
        File(dataFolder, "recipes/epic_sword.yml").writeText(
            """
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
                  xp: 10
            """.trimIndent()
        )

        RecipeCreatorConfigWriter(plugin).saveRecipeYaml(pending())

        val saved = YamlConfiguration.loadConfiguration(File(dataFolder, "recipes/epic_sword.yml"))
        assertEquals("netherite_sword", saved.getString("output"))
        assertEquals(4, saved.getInt("craft-limits.player"))
        assertFalse(saved.contains("permission"))
        assertEquals(listOf("&7Hand written"), saved.getStringList("lore"))
        assertEquals("100", saved.getString("price.value"))
        assertEquals(listOf("Costs coins."), saved.getComments("price"))
        assertEquals("give_xp", saved.getMapList("effects").first()["id"])
        assertEquals(emptyList<Any>(), saved.getList("crafting-conditions"))
    }
}
