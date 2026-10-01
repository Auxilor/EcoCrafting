package com.exanthiax.ecocrafting.recipegui.integration

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuilderEditPreservationTest {

    private fun yaml(text: String) = YamlConfiguration().apply { loadFromString(text) }

    private val existing = yaml(
        """
        enabled: true
        type: crafting_table
        output: diamond_sword
        permission: old.node
        lore:
          - "&7Hand written"
        price:
          value: "100"
          type: coins
        effects:
          - id: give_xp
            args:
              amount: 10
        crafting-conditions:
          - id: is_sneaking
        """.trimIndent()
    )

    @Test
    fun `keys the builder does not manage are carried over`() {
        val carried = yaml(carriedOverYaml(existing))
        assertTrue(carried.getBoolean("enabled"))
        assertEquals("100", carried.getString("price.value"))
        assertEquals("coins", carried.getString("price.type"))
        assertEquals(10, carried.getMapList("effects").first().let { (it["args"] as Map<*, *>)["amount"] })
        assertEquals(listOf("&7Hand written"), carried.getStringList("lore"))
        assertEquals("is_sneaking", carried.getMapList("crafting-conditions").first()["id"])
    }

    @Test
    fun `keys the builder manages are not carried over`() {
        val carried = yaml(carriedOverYaml(existing))
        assertFalse(carried.contains("type"))
        assertFalse(carried.contains("output"))
        assertFalse(carried.contains("permission"))
    }

    @Test
    fun `a new recipe carries nothing over`() {
        assertEquals("", carriedOverYaml(null))
    }

    @Test
    fun `placeholder defaults are only written for keys the file does not already have`() {
        assertEquals(
            listOf("visibility-conditions: []", "unlock-conditions: []"),
            builderDefaultLines(existing)
        )
        assertEquals(
            listOf("lore: []", "visibility-conditions: []", "crafting-conditions: []", "unlock-conditions: []"),
            builderDefaultLines(null)
        )
    }

    @Test
    fun `stonecutter output 0 keeps its extra keys and later outputs are untouched`() {
        val stonecutter = yaml(
            """
            type: stonecutter
            outputs:
              - item: stone_slab
                id: slab
                give-result-item: true
                lore:
                  - "&7Slab"
                price:
                  value: "5"
                  type: coins
                effects:
                  - id: give_xp
                crafting-conditions:
                  - id: is_sneaking
                visibility-conditions:
                  - id: is_flying
                conditions:
                  - id: is_swimming
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
            """.trimIndent()
        )
        val outputs = yaml(stonecutterOutputsYaml(stonecutter, "chiseled_stone_bricks", false)).getMapList("outputs")

        assertEquals(3, outputs.size)
        assertEquals("chiseled_stone_bricks", outputs[0]["item"])
        assertEquals(false, outputs[0]["give-result-item"])
        assertEquals("slab", outputs[0]["id"])
        assertEquals(listOf("&7Slab"), outputs[0]["lore"])
        assertEquals(mapOf("value" to "5", "type" to "coins"), outputs[0]["price"])
        assertEquals(listOf(mapOf("id" to "give_xp")), outputs[0]["effects"])
        assertEquals(listOf(mapOf("id" to "is_sneaking")), outputs[0]["crafting-conditions"])
        assertEquals(listOf(mapOf("id" to "is_flying")), outputs[0]["visibility-conditions"])
        assertEquals(listOf(mapOf("id" to "is_swimming")), outputs[0]["conditions"])
        assertEquals(stonecutter.getMapList("outputs").drop(1), outputs.drop(1))
    }

    @Test
    fun `a new stonecutter output gets the default lore`() {
        val outputs = yaml(stonecutterOutputsYaml(null, "stone_slab", true)).getMapList("outputs")
        assertEquals(listOf(mapOf("item" to "stone_slab", "lore" to emptyList<String>(), "give-result-item" to true)), outputs)
    }
}
