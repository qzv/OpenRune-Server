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
    fun `soul split heals half the damage rounded down`() {
        assertEquals(6, NexZarosRules.soulSplitHeal(12))
        assertEquals(6, NexZarosRules.soulSplitHeal(13))
        assertEquals(11, NexZarosRules.soulSplitHeal(23))
    }
}
