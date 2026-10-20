package org.rsmod.content.minigames.colosseum.modifiers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FrailtyRulesTest {
    @Test
    fun `the cap removes the level's share of the base hitpoints, rounding the share down`() {
        assertEquals(90, FrailtyRules.cap(1, 99))
        assertEquals(80, FrailtyRules.cap(2, 99))
        assertEquals(60, FrailtyRules.cap(3, 99))
        assertEquals(63, FrailtyRules.cap(1, 70))
        assertEquals(77, FrailtyRules.cap(1, 85))
    }
}
