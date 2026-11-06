package com.exanthiax.ecocrafting.recipegui.integration

import com.willfp.eco.core.items.Items
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
