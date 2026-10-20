package org.rsmod.content.bosses.yama

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class YamaRunDrainTest {
    @ParameterizedTest
    @CsvSource("0,0,100", "1,0,0", "4,0,100", "5,3,300", "8,2,300")
    fun `run energy drains every fourth tick plus damage taken`(
        tick: Int,
        damage: Int,
        expected: Int,
    ) {
        assertEquals(expected, YamaRunDrain.amount(tick, damage))
    }

    @Test
    fun `healing never refunds run energy`() {
        assertEquals(0, YamaRunDrain.amount(1, -20))
    }
}
