package com.exanthiax.ecocrafting.recipegui.integration

import com.exanthiax.ecocrafting.limit.model.UNLIMITED
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
        craft-limits:
          player: 3
        lore:
          - "&7Hand written"
        price:
          value: "100"
          type: coins
        effects:
          - id: give_xp
            args:
              xp: 10
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
        assertEquals(10, carried.getMapList("effects").first().let { (it["args"] as Map<*, *>)["xp"] })
        assertEquals(listOf("&7Hand written"), carried.getStringList("lore"))
        assertEquals("is_sneaking", carried.getMapList("crafting-conditions").first()["id"])
    }

    @Test
    fun `keys the builder manages are not carried over`() {
        val carried = yaml(carriedOverYaml(existing))
        assertFalse(carried.contains("type"))
        assertFalse(carried.contains("output"))
        assertFalse(carried.contains("permission"))
        assertFalse(carried.contains("craft-limits"))
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
                give-result-item: true
                craft-limits:
                  player: 2
                price:
                  value: "5"
                  type: coins
                effects:
                  - id: give_xp
              - item: stone_stairs
                price:
                  value: "9"
                  type: coins
            """.trimIndent()
        )
        val outputs = yaml(stonecutterOutputsYaml(stonecutter, "stone_bricks", false, UNLIMITED, 7)).getMapList("outputs")

        assertEquals(2, outputs.size)
        assertEquals("stone_bricks", outputs[0]["item"])
        assertEquals(false, outputs[0]["give-result-item"])
        assertEquals(mapOf("global" to 7), outputs[0]["craft-limits"])
        assertEquals(mapOf("value" to "5", "type" to "coins"), outputs[0]["price"])
        assertEquals(listOf(mapOf("id" to "give_xp")), outputs[0]["effects"])
        assertEquals("stone_stairs", outputs[1]["item"])
        assertEquals(mapOf("value" to "9", "type" to "coins"), outputs[1]["price"])
    }

    @Test
    fun `a new stonecutter output gets the default lore and no limits block when unlimited`() {
        val outputs = yaml(stonecutterOutputsYaml(null, "stone_slab", true, UNLIMITED, UNLIMITED)).getMapList("outputs")
        assertEquals(listOf(mapOf("item" to "stone_slab", "lore" to emptyList<String>(), "give-result-item" to true)), outputs)
    }
}
