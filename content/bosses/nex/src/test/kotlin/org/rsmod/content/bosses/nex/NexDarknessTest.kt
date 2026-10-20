package org.rsmod.content.bosses.nex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NexDarknessTest {
    private fun run(distances: List<Int>): String {
        var counter = 0
        var messaged = false
        val out = StringBuilder()
        for (distance in distances) {
            when (NexDarkness.engulf(counter, distance, messaged)) {
                NexEngulf.Message -> {
                    messaged = true
                    out.append('M')
                }
                NexEngulf.Hit -> out.append('H')
                NexEngulf.None -> out.append('.')
            }
            counter = NexDarkness.step(counter, distance)
        }
        return out.toString()
    }

    private fun ticks(vararg runs: Pair<Int, Int>): List<Int> =
        runs.flatMap { (count, distance) -> List(count) { distance } }

    @ParameterizedTest
    @CsvSource("0,50", "1,50", "2,60", "3,80", "5,120", "8,180", "9,200", "15,200")
    fun `fade follows the distance to the footprint`(distance: Int, fade: Int) {
        assertEquals(fade, NexDarkness.fade(distance))
    }

    @Test
    fun `eight close ticks engulf and hits follow every close tick`() {
        assertEquals(".......MHHH", run(ticks(11 to 1)))
    }

    @Test
    fun `time away lowers the counter before the message`() {
        assertEquals("............M", run(ticks(2 to 1, 3 to 4, 8 to 0)))
        assertEquals("......." + ".." + "..M", run(ticks(7 to 1, 2 to 5, 3 to 1)))
    }

    @Test
    fun `stepping out and back in briefly does not resume the hits`() {
        val result = run(ticks(12 to 1, 5 to 3, 3 to 1))
        assertEquals(".......MHHHH" + "....." + "...", result)
    }

    @Test
    fun `the engulf message is sent once per darkness`() {
        val result = run(ticks(9 to 1, 1 to 3, 3 to 1))
        assertEquals(".......MH" + "." + ".HH", result)
    }
}
