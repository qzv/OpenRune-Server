package org.rsmod.content.minigames.colosseum.sol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class SolRulesTest {
    @Test
    fun `a perfect parry buffs attacks from the parry tick to 5 ticks after`() {
        assertTrue(SolRules.parryBuffActive(parryTick = 100, usedTick = 0, now = 100))
        assertTrue(SolRules.parryBuffActive(parryTick = 100, usedTick = 0, now = 105))
        assertFalse(SolRules.parryBuffActive(parryTick = 100, usedTick = 0, now = 106))
        assertFalse(SolRules.parryBuffActive(parryTick = 100, usedTick = 0, now = 99))
        assertFalse(SolRules.parryBuffActive(parryTick = 0, usedTick = 0, now = 3))
    }

    @Test
    fun `the parry buff is spent by the first attack but covers all of that attack's rolls`() {
        assertTrue(SolRules.parryBuffActive(parryTick = 100, usedTick = 102, now = 102))
        assertFalse(SolRules.parryBuffActive(parryTick = 100, usedTick = 102, now = 103))
        assertFalse(SolRules.parryBuffActive(parryTick = 100, usedTick = 100, now = 104))
    }

    @Test
    fun `a new parry gives a new buff after an earlier one was spent`() {
        assertTrue(SolRules.parryBuffActive(parryTick = 150, usedTick = 102, now = 152))
    }

    @Test
    fun `phase thresholds use exact hitpoints`() {
        assertTrue(SolRules.thresholdReached(1350, 1500, 90))
        assertFalse(SolRules.thresholdReached(1351, 1500, 90))
        assertFalse(SolRules.thresholdReached(1364, 1500, 90))
        assertTrue(SolRules.thresholdReached(1125, 1500, 75))
        assertTrue(SolRules.thresholdReached(150, 1500, 10))
        assertFalse(SolRules.thresholdReached(151, 1500, 10))
    }

    @Test
    fun `triple uses the shorter profile above half health`() {
        assertArrayEquals(intArrayOf(15, 25, 35), SolRules.tripleDamages(1))
        assertArrayEquals(intArrayOf(3, 6, 9), SolRules.tripleOffsets(2))
        assertArrayEquals(intArrayOf(15, 30, 45), SolRules.tripleDamages(3))
        assertArrayEquals(intArrayOf(3, 6, 10), SolRules.tripleOffsets(5))
    }

    @Test
    fun `specials wait for the cooldown after the first arming`() {
        assertFalse(SolRules.specialEligible(500, -1, SolRules.FIRST_SPECIAL_COOLDOWN))
        assertFalse(SolRules.specialEligible(126, 100, SolRules.FIRST_SPECIAL_COOLDOWN))
        assertTrue(SolRules.specialEligible(127, 100, SolRules.FIRST_SPECIAL_COOLDOWN))
        assertFalse(SolRules.specialEligible(128, 100, SolRules.SPECIAL_COOLDOWN))
        assertTrue(SolRules.specialEligible(129, 100, SolRules.SPECIAL_COOLDOWN))
    }

    @Test
    fun `beam never hits the tile next to the crystal`() {
        val crystal = CoordGrid(1820, 3113, 0)
        assertEquals(1, SolRules.beamDistance(crystal, 0, -1, CoordGrid(1820, 3112, 0)))
        assertNull(SolRules.beamDelay(1))
        assertNull(SolRules.beamDistance(crystal, 0, -1, CoordGrid(1821, 3110, 0)))
    }

    @Test
    fun `near beams land on the fire tick and far beams one tick later`() {
        val crystal = CoordGrid(1831, 3106, 0)
        val near = SolRules.beamDistance(crystal, -1, 0, CoordGrid(1829, 3106, 0))
        assertEquals(2, near)
        assertEquals(0, SolRules.beamDelay(near!!))
        assertEquals(0, SolRules.beamDelay(5))
        assertEquals(1, SolRules.beamDelay(6))
        assertEquals(1, SolRules.beamDelay(13))
    }

    @Test
    fun `sand drops blocked and duplicate rolls without rerolling`() {
        val centre = CoordGrid(1824, 3106, 0)
        val sand = setOf(CoordGrid(1825, 3106, 0))
        val rolls =
            listOf(
                CoordGrid(1825, 3106, 0),
                CoordGrid(1822, 3104, 0),
                CoordGrid(1822, 3104, 0),
                centre,
                CoordGrid(1820, 3110, 0),
            )
        val picks = SolRules.sandPicks(centre, rolls) { it in sand }
        assertEquals(listOf(centre, CoordGrid(1822, 3104, 0), CoordGrid(1820, 3110, 0)), picks)
    }
}
