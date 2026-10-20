package org.rsmod.content.bosses.yama

import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YamaCrashTest {
    @Test
    fun `crash opens on a cardinal line then the diagonal then the other cardinal`() {
        assertEquals(
            listOf(CrashLine.HORIZONTAL, CrashLine.DIAGONAL, CrashLine.VERTICAL),
            YamaCrash.order(CrashLine.HORIZONTAL),
        )
        assertEquals(
            listOf(CrashLine.VERTICAL, CrashLine.DIAGONAL, CrashLine.HORIZONTAL),
            YamaCrash.order(CrashLine.VERTICAL),
        )
    }

    @Test
    fun `five set crashes add the anti diagonal and repeat the opening line`() {
        assertEquals(
            listOf(
                CrashLine.VERTICAL,
                CrashLine.DIAGONAL,
                CrashLine.HORIZONTAL,
                CrashLine.ANTI_DIAGONAL,
                CrashLine.VERTICAL,
            ),
            YamaCrash.order(CrashLine.VERTICAL, sets = 5),
        )
    }

    @Test
    fun `anti diagonal impacts and shadows mirror the diagonal`() {
        assertEquals(
            setOf(CrashTile(0, 0, 0), CrashTile(-2, -2, 3), CrashTile(2, 2, 3)),
            YamaCrash.impacts(CrashLine.ANTI_DIAGONAL).toSet(),
        )
        val shadows = YamaCrash.shadows(CrashLine.ANTI_DIAGONAL)
        assertEquals(41, shadows.size)
        assertTrue(shadows.all { it.dx * it.dz >= 0 })
        assertEquals(
            YamaCrash.shadows(CrashLine.DIAGONAL)
                .map { CrashTile(-it.dx, it.dz, it.delay) }
                .toSet(),
            shadows.toSet(),
        )
    }

    @Test
    fun `the diagonal cannot open a crash`() {
        assertThrows(IllegalArgumentException::class.java) {
            YamaCrash.order(CrashLine.DIAGONAL)
        }
    }

    @Test
    fun `horizontal impacts are the centre and two tiles three either side`() {
        assertEquals(
            setOf(CrashTile(0, 0, 0), CrashTile(-3, 0, 3), CrashTile(3, 0, 3)),
            YamaCrash.impacts(CrashLine.HORIZONTAL).toSet(),
        )
    }

    @Test
    fun `vertical impacts are the centre and two tiles three north and south`() {
        assertEquals(
            setOf(CrashTile(0, 0, 0), CrashTile(0, -3, 3), CrashTile(0, 3, 3)),
            YamaCrash.impacts(CrashLine.VERTICAL).toSet(),
        )
    }

    @Test
    fun `diagonal impacts sit two tiles off the centre on the anti diagonal`() {
        assertEquals(
            setOf(CrashTile(0, 0, 0), CrashTile(-2, 2, 3), CrashTile(2, -2, 3)),
            YamaCrash.impacts(CrashLine.DIAGONAL).toSet(),
        )
    }

    @Test
    fun `horizontal shadows form a bowtie of thirty one tiles`() {
        val shadows = YamaCrash.shadows(CrashLine.HORIZONTAL)
        assertEquals(31, shadows.size)
        assertEquals(31, shadows.map { it.dx to it.dz }.toSet().size)
        assertEquals(14, shadows.count { it.delay == 8 })
        assertEquals(10, shadows.count { it.delay == 15 })
        assertEquals(6, shadows.count { it.delay == 23 })
        assertEquals(listOf(CrashTile(0, 0, 30)), shadows.filter { it.delay == 30 })
        assertTrue(shadows.all { abs(it.dz) <= abs(it.dx) })
    }

    @Test
    fun `vertical shadows are the horizontal bowtie turned a quarter`() {
        val turned =
            YamaCrash.shadows(CrashLine.HORIZONTAL).map { CrashTile(it.dz, it.dx, it.delay) }
        assertEquals(turned.toSet(), YamaCrash.shadows(CrashLine.VERTICAL).toSet())
    }

    @Test
    fun `diagonal shadows cover forty one tiles with delay falling six per step`() {
        val shadows = YamaCrash.shadows(CrashLine.DIAGONAL)
        assertEquals(41, shadows.size)
        assertEquals(12, shadows.count { it.delay == 0 })
        assertEquals(10, shadows.count { it.delay == 6 })
        assertEquals(4, shadows.count { it.delay == 24 })
        assertTrue(shadows.all { it.dx * it.dz <= 0 })
        assertEquals(CrashTile(-1, 1, 18), shadows.single { it.dx == -1 && it.dz == 1 })
        assertEquals(CrashTile(3, -2, 0), shadows.single { it.dx == 3 && it.dz == -2 })
    }
}
