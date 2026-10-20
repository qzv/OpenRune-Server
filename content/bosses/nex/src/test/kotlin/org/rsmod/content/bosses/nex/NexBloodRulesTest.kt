package org.rsmod.content.bosses.nex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NexBloodRulesTest {
    @ParameterizedTest
    @CsvSource(
        "1,1",
        "6,6",
        "7,8",
        "8,9",
        "9,10",
        "10,11",
        "11,12",
        "12,13",
        "13,14",
        "14,16",
    )
    fun `blood attacks heal Nex the recorded amount per damage`(damage: Int, heal: Int) {
        assertEquals(heal, NexBloodRules.standardHeal(damage))
    }

    @Test
    fun `blood attacks drain prayer by half the damage rounded up`() {
        assertEquals(0, NexBloodRules.standardPrayerDrain(0))
        assertEquals(6, NexBloodRules.standardPrayerDrain(11))
        assertEquals(7, NexBloodRules.standardPrayerDrain(13))
        assertEquals(5, NexBloodRules.standardPrayerDrain(9))
    }

    @ParameterizedTest
    @CsvSource("24,36", "29,43", "30,45", "32,48", "33,49", "34,51")
    fun `a failed sacrifice heals Nex half again the damage`(damage: Int, heal: Int) {
        assertEquals(heal, NexBloodRules.sacrificeHeal(damage))
    }

    @Test
    fun `a failed sacrifice drains a quarter of current prayer`() {
        assertEquals(24, NexBloodRules.sacrificePrayerDrain(98))
        assertEquals(21, NexBloodRules.sacrificePrayerDrain(85))
        assertEquals(0, NexBloodRules.sacrificePrayerDrain(3))
    }

    @Test
    fun `escaping a sacrifice needs seven tiles from the nearest Nex tile`() {
        assertFalse(NexBloodRules.escapedSacrifice(1))
        assertFalse(NexBloodRules.escapedSacrifice(6))
        assertTrue(NexBloodRules.escapedSacrifice(7))
        assertTrue(NexBloodRules.escapedSacrifice(12))
    }

    @Test
    fun `the siphon converts damage from two to eight ticks after the cast`() {
        val cast = 100
        assertFalse(NexBloodRules.siphoning(cast + 1, cast + 2, cast + 8))
        assertTrue(NexBloodRules.siphoning(cast + 2, cast + 2, cast + 8))
        assertTrue(NexBloodRules.siphoning(cast + 8, cast + 2, cast + 8))
        assertFalse(NexBloodRules.siphoning(cast + 9, cast + 2, cast + 8))
        assertFalse(NexBloodRules.siphoning(cast, Int.MAX_VALUE, Int.MIN_VALUE))
    }
}
