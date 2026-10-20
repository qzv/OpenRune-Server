package org.rsmod.content.minigames.colosseum

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ColosseumGloryTest {
    @Test
    fun `durations format like the wave completion message`() {
        assertEquals("0:12.60", ColosseumGlory.formatDuration(21))
        assertEquals("7:22.20", ColosseumGlory.formatDuration(737))
        assertEquals("11:35.40", ColosseumGlory.formatDuration(1159))
        assertEquals("1:06:58.80", ColosseumGlory.formatDuration(6698))
        assertEquals("1:23:47.40", ColosseumGlory.formatDuration(8379))
    }

    @Test
    fun `durations without precise timing round to the nearest second`() {
        assertEquals("0:23", ColosseumGlory.formatSeconds(38))
        assertEquals("1:28", ColosseumGlory.formatSeconds(146))
        assertEquals("4:50", ColosseumGlory.formatSeconds(483))
        assertEquals("22:44", ColosseumGlory.formatSeconds(2274))
    }

    @Test
    fun `glory adds completion, speed and modifier glory`() {
        assertEquals(
            679,
            ColosseumGlory.earned(
                wave = 1,
                durationTicks = 21,
                damageTaken = 28,
                modifierGlory = 100,
            ),
        )
        assertEquals(
            1444,
            ColosseumGlory.earned(
                wave = 2,
                durationTicks = 28,
                damageTaken = 99,
                modifierGlory = 300,
            ),
        )
        assertEquals(
            1418,
            ColosseumGlory.earned(
                wave = 3,
                durationTicks = 244,
                damageTaken = 196,
                modifierGlory = 350,
            ),
        )
        assertEquals(
            7871,
            ColosseumGlory.earned(
                wave = 11,
                durationTicks = 39,
                damageTaken = 50,
                modifierGlory = 1700,
            ),
        )
    }

    @Test
    fun `slow waves lose the speed bonus`() {
        assertEquals(
            450,
            ColosseumGlory.earned(
                wave = 2,
                durationTicks = 822,
                damageTaken = 484,
                modifierGlory = 250,
            ),
        )
    }

    @Test
    fun `sol wave adds the completion bonus`() {
        assertEquals(
            4300,
            ColosseumGlory.earned(
                wave = 12,
                durationTicks = 737,
                damageTaken = 2435,
                modifierGlory = 2100,
            ),
        )
        assertEquals(
            8432,
            ColosseumGlory.earned(
                wave = 12,
                durationTicks = 139,
                damageTaken = 60,
                modifierGlory = 1900,
            ),
        )
    }

    @Test
    fun `flawless waves earn the damage bonus`() {
        assertEquals(
            100 + 479 + 100 + 100,
            ColosseumGlory.earned(
                wave = 1,
                durationTicks = 21,
                damageTaken = 0,
                modifierGlory = 100,
            ),
        )
    }
}
