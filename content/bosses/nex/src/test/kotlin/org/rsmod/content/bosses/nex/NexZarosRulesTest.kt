package org.rsmod.content.bosses.nex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class NexZarosRulesTest {
    @Test
    fun `forms change on the first of every four attacks`() {
        assertEquals(NexForm.SoulSplit, NexZarosRules.formChange(1))
        assertEquals(NexForm.Deflect, NexZarosRules.formChange(5))
        assertEquals(NexForm.Plain, NexZarosRules.formChange(9))
        assertEquals(NexForm.SoulSplit, NexZarosRules.formChange(13))
        assertNull(NexZarosRules.formChange(2))
        assertNull(NexZarosRules.formChange(8))
        assertNull(NexZarosRules.formChange(0))
    }

    @Test
    fun `soul split heals half the damage rounded down`() {
        assertEquals(6, NexZarosRules.soulSplitHeal(12))
        assertEquals(6, NexZarosRules.soulSplitHeal(13))
        assertEquals(11, NexZarosRules.soulSplitHeal(23))
    }

    @Test
    fun `wrath reaches three tiles from the centre`() {
        val centre = CoordGrid(2914, 5197, 0)
        assertTrue(NexZarosRules.inWrath(centre, CoordGrid(2917, 5200, 0)))
        assertTrue(NexZarosRules.inWrath(centre, CoordGrid(2911, 5197, 0)))
        assertFalse(NexZarosRules.inWrath(centre, CoordGrid(2918, 5197, 0)))
    }

    @Test
    fun `respawn waits 36 ticks then lands on the session's three-tick cycle`() {
        assertEquals(1036, NexZarosRules.respawnTick(now = 1000, anchor = 100))
        assertEquals(1037, NexZarosRules.respawnTick(now = 1000, anchor = 101))
        assertEquals(1038, NexZarosRules.respawnTick(now = 1000, anchor = 102))
        assertEquals(1036, NexZarosRules.respawnTick(now = 1000, anchor = 1036))
    }
}
