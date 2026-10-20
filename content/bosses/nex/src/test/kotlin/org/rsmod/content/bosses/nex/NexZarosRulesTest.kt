package org.rsmod.content.bosses.nex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

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
    fun `respawn waits 36 ticks then lands on the session's three-tick cycle`() {
        assertEquals(1036, NexZarosRules.respawnTick(now = 1000, anchor = 100))
        assertEquals(1037, NexZarosRules.respawnTick(now = 1000, anchor = 101))
        assertEquals(1038, NexZarosRules.respawnTick(now = 1000, anchor = 102))
        assertEquals(1036, NexZarosRules.respawnTick(now = 1000, anchor = 1036))
    }
}
