package com.exanthiax.ecocrafting.recipegui.integration

import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.recipegui.service.PendingRecipe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import wiki.ecohub.game.item.Items
import java.io.File

class RecipeCreatorConfigWriterTest {

    private val item: ItemStack = mockk {
        every { isEmpty } returns false
    }

    @BeforeEach
    fun setUp() {
        mockkStatic(Items::class)
    }

    @Test
    fun `uses the exact lookup string`() {
        every { Items.toExactLookupString(item) } returns "ecoitems:uncut_topaz 4"

        assertEquals("ecoitems:uncut_topaz 4", itemLookupString(item))
    }

    @Test
    fun `snbt survives yaml`() {
        val snbt = """{DataVersion:4790,id:"minecraft:stone",components:{"minecraft:custom_data":{a:"b\\c"}}}"""
        every { Items.toExactLookupString(item) } returns snbt

        val yaml = YamlConfiguration()
        yaml.loadFromString("recipe:\n  - ${itemYamlValue(item)}\noutput: ${itemYamlValue(item)}\n")

        assertEquals(listOf(snbt), yaml.getStringList("recipe"))
        assertEquals(snbt, yaml.getString("output"))
    }

    @Test
    fun `saved recipe keeps snbt`(@TempDir dataFolder: File) {
        val snbt = """{DataVersion:4790,id:"minecraft:stone",components:{"minecraft:custom_data":{a:"b\\c"}}}"""
        every { Items.toExactLookupString(item) } returns snbt
        every { item.type } returns Material.STONE
        mockkObject(Material.STONE)
        every { Material.STONE.isAir } returns false
        val plugin = mockk<EcoCraftingPlugin> {
            every { getDataFolder() } returns dataFolder
        }

        try {
            RecipeCreatorConfigWriter(plugin).saveRecipeYaml(
                PendingRecipe(
                    typeKey = "crafting_table",
                    parts = mapOf(0 to item),
                    output = item,
                    shapeless = false,
                    symmetry = false,
                    supportCrafter = false,
                    cookTime = null,
                    experience = 0.0,
                    profession = "",
                    minLevel = 1,
                    chance = 1.0,
                    wanderingTrader = false,
                    villagerXp = 0,
                    id = "snbt_recipe",
                    permission = "",
                    category = "",
                    lockedByDefault = false,
                    showWhenLocked = false,
                    repairCost = 0,
                    brewTime = null
                )
            )
        } finally {
            unmockkObject(Material.STONE)
        }

        val saved = YamlConfiguration.loadConfiguration(File(dataFolder, "recipes/snbt_recipe.yml"))
        assertEquals(snbt, saved.getStringList("recipe").first())
        assertEquals(snbt, saved.getString("output"))
    }

    @Test
    fun `lookup string stays unquoted`() {
        val snbt = """{DataVersion:4790,id:"minecraft:stone"}"""
        every { Items.toExactLookupString(item) } returns snbt

        assertEquals(snbt, itemLookupString(item))
    }

    @Test
    fun `key is not quoted in yaml`() {
        every { Items.toExactLookupString(item) } returns "nexo:forest_axe 2"

        assertEquals("nexo:forest_axe 2", itemYamlValue(item))
    }

    @Test
    fun `empty item serializes to air`() {
        assertEquals("air", itemLookupString(mockk<ItemStack> { every { isEmpty } returns true }))
        assertEquals("air", itemLookupString(null))
    }
}
