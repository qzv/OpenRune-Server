package org.rsmod.content.bosses.nex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NexDrainsTest {
    @Test
    fun `the virus drains the stat of the highest attack bonus`() {
        assertEquals(NexDrains.RANGED, NexDrains.drainedStat(melee = 20, ranged = 120, magic = 10))
        assertEquals(NexDrains.MAGIC, NexDrains.drainedStat(melee = 20, ranged = 30, magic = 90))
        assertEquals(NexDrains.ATTACK, NexDrains.drainedStat(melee = 140, ranged = 0, magic = -20))
    }

    @ParameterizedTest
    @CsvSource("99,15", "114,18", "116,18", "117,18")
    fun `blindness drains 15 percent of current attack rounded up`(attack: Int, drain: Int) {
        assertEquals(drain, NexDrains.blindDrain(attack))
    }
}
