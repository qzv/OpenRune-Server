package org.rsmod.content.bosses.nex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NexThresholdsTest {
    @ParameterizedTest
    @CsvSource("80,2720,2550", "60,2040,1870", "40,1360,1190", "20,680,510")
    fun `thresholds and floors match the recorded values`(percent: Int, line: Int, floor: Int) {
        assertEquals(line, NexThresholds.threshold(BASE_HP, percent))
        assertEquals(floor, NexThresholds.floor(BASE_HP, percent))
    }

    @Test
    fun `the line is due at or below the threshold`() {
        assertFalse(NexThresholds.crossed(2721, BASE_HP, 80))
        assertTrue(NexThresholds.crossed(2720, BASE_HP, 80))
    }

    @Test
    fun `hits above the floor are unchanged`() {
        assertEquals(62, NexThresholds.clamp(2770, 62, 2550))
    }

    @Test
    fun `a hit that crosses the floor is cut to reach it exactly`() {
        assertEquals(74, NexThresholds.clamp(2624, 100, 2550))
        assertEquals(29, NexThresholds.clamp(1899, 40, 1870))
    }

    @Test
    fun `hits at the floor deal nothing`() {
        assertEquals(0, NexThresholds.clamp(2550, 35, 2550))
    }

    private companion object {
        private const val BASE_HP = 3400
    }
}
