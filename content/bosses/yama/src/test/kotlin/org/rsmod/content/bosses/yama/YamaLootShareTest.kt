package org.rsmod.content.bosses.yama

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YamaLootShareTest {
    @Test
    fun `share is damage over max hitpoints`() {
        assertEquals(0.4, YamaLootShare.of(1000, 2500), 1e-9)
        assertEquals(0.0, YamaLootShare.of(0, 2500), 1e-9)
    }

    @Test
    fun `share is capped at the whole kill when flare heals push damage past max hitpoints`() {
        assertEquals(1.0, YamaLootShare.of(2900, 2500), 1e-9)
    }

    @Test
    fun `quantities scale linearly and never drop below one`() {
        assertEquals(16000, YamaLootShare.quantity(40000, 0.4))
        assertEquals(1, YamaLootShare.quantity(2, 0.1))
        assertEquals(40000, YamaLootShare.quantity(40000, 1.0))
    }

    @Test
    fun `unique roll is kept with probability equal to the share`() {
        val half = YamaLootShare.SCALE / 2
        val kept =
            (0 until YamaLootShare.SCALE step 1000).count { YamaLootShare.keepsUnique(0.5, it) }
        assertEquals(500, kept)
        assertTrue(YamaLootShare.keepsUnique(0.5, half - 1))
        assertFalse(YamaLootShare.keepsUnique(0.5, half))
        assertTrue(YamaLootShare.keepsUnique(1.0, YamaLootShare.SCALE - 1))
        assertFalse(YamaLootShare.keepsUnique(0.0, 0))
    }
}
