package org.rsmod.content.bosses.nex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NexLootRulesTest {
    @Test
    fun `a solo killer has the whole share and is the MVP`() {
        val awards = NexLootRules.awards(mapOf("a" to 3400), mapOf("a" to 3400))
        assertEquals(NexAward(1.0, 1, true), awards["a"])
    }

    @Test
    fun `shares count all fight damage while the MVP is the top damage on Nex`() {
        val damage = mapOf("a" to 3000, "b" to 1000)
        val nex = mapOf("a" to 1500, "b" to 1700)
        val awards = NexLootRules.awards(damage, nex)
        assertEquals(0.75, awards.getValue("a").share, 1e-9)
        assertEquals(0.25, awards.getValue("b").share, 1e-9)
        assertFalse(awards.getValue("a").mvp)
        assertTrue(awards.getValue("b").mvp)
        assertEquals(2, awards.getValue("a").team)
    }

    @Test
    fun `players without damage get no award and do not count towards the team`() {
        val awards = NexLootRules.awards(mapOf("a" to 500, "b" to 0), mapOf("a" to 0))
        assertEquals(setOf("a"), awards.keys)
        assertEquals(NexAward(1.0, 1, false), awards["a"])
    }

    @Test
    fun `no damage at all gives no awards`() {
        assertTrue(NexLootRules.awards(mapOf("a" to 0), mapOf("a" to 0)).isEmpty())
    }

    @Test
    fun `the MVP gets ten percent more of stackable commons`() {
        val solo = NexAward(1.0, 1, true)
        assertEquals(11000, NexLootRules.quantity(10000, solo, stackable = true))
        assertEquals(93, NexLootRules.quantity(85, solo, stackable = true))
        assertEquals(10000, NexLootRules.quantity(10000, solo.copy(mvp = false), stackable = true))
    }

    @Test
    fun `non stackable consumables never exceed the rolled amount`() {
        val solo = NexAward(1.0, 1, true)
        assertEquals(10, NexLootRules.quantity(10, solo, stackable = false))
        assertEquals(5, NexLootRules.quantity(5, solo, stackable = false))
        assertEquals(3, NexLootRules.quantity(10, NexAward(0.3, 1, false), stackable = false))
    }

    @Test
    fun `equal team shares keep the solo amount and quantities never fall below one`() {
        assertEquals(600, NexLootRules.quantity(600, NexAward(0.5, 2, false), stackable = true))
        assertEquals(1, NexLootRules.quantity(2, NexAward(0.1, 2, false), stackable = true))
        assertEquals(0, NexLootRules.quantity(0, NexAward(1.0, 1, true), stackable = true))
    }

    @Test
    fun `scaled quantities clamp at the stack limit instead of overflowing`() {
        val award = NexAward(1.0, 3, true)
        assertEquals(Int.MAX_VALUE, NexLootRules.quantity(Int.MAX_VALUE, award, stackable = true))
    }

    @Test
    fun `unique chance is one in 43 scaled by share and the MVP bonus`() {
        val rate = NexLootRules.UNIQUE_RATE
        val range = rate * NexLootRules.SCALE
        fun hitsOutOf(award: NexAward): Int =
            (0 until range step 1000).count { NexLootRules.hits(award, rate, it) }
        val samples = range / 1000
        assertEquals(samples * 11 / 10 / rate, hitsOutOf(NexAward(1.0, 1, true)))
        assertEquals(samples / rate, hitsOutOf(NexAward(1.0, 1, false)))
        assertEquals(samples / 2 / rate, hitsOutOf(NexAward(0.5, 2, false)))
    }

    @Test
    fun `chance rolls are exact at the boundary`() {
        val award = NexAward(0.5, 2, false)
        assertTrue(NexLootRules.hits(award, 500, NexLootRules.SCALE / 2 - 1))
        assertFalse(NexLootRules.hits(award, 500, NexLootRules.SCALE / 2))
        assertFalse(NexLootRules.hits(award, 0, 0))
    }

    @ParameterizedTest
    @CsvSource(
        "308,3:05",
        "290,2:54",
        "286,2:52",
        "303,3:02",
        "316,3:10",
        "306,3:04",
        "214,2:08",
        "246,2:28",
        "281,2:49",
        "168,1:41",
        "175,1:45",
    )
    fun `fight duration rounds ticks to the nearest second`(ticks: Int, shown: String) {
        assertEquals(shown, NexLootRules.clock(ticks))
    }
}
