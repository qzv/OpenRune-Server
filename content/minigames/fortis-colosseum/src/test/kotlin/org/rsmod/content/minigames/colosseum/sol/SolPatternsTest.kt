package org.rsmod.content.minigames.colosseum.sol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SolPatternsTest {
    private val open: (Int, Int) -> Boolean = { _, _ -> true }

    private fun List<SolTile>.coords() = map { it.dx to it.dz }.toSet()

    @Test
    fun `player side follows the footprint edges`() {
        assertEquals(SolSide.South, SolPatterns.side(1, -1))
        assertEquals(SolSide.North, SolPatterns.side(4, 8))
        assertEquals(SolSide.West, SolPatterns.side(-2, 0))
        assertEquals(SolSide.NorthEast, SolPatterns.side(5, 5))
        assertEquals(SolSide.Under, SolPatterns.side(2, 2))
    }

    @Test
    fun `every slam covers the footprint with centre delays`() {
        val tiles = SolPatterns.spear(1, SolSide.Under, open)
        assertEquals(25, tiles.size)
        assertEquals(0, tiles.single { it.dx == 2 && it.dz == 2 }.delay)
        assertEquals(4, tiles.single { it.dx == 0 && it.dz == 0 }.delay)
    }

    @Test
    fun `southern spear one leaves columns 0 2 4 safe beyond the front row`() {
        val tiles = SolPatterns.spear(1, SolSide.South, open).coords()
        assertTrue((0..4).all { (it to -1) in tiles })
        assertTrue((1 to -3) in tiles && (3 to -3) in tiles)
        assertFalse((0 to -3) in tiles || (2 to -3) in tiles || (4 to -3) in tiles)
    }

    @Test
    fun `southern spear one front row is delayed six cycles`() {
        val front = SolPatterns.spear(1, SolSide.South, open).single { it.dx == 0 && it.dz == -1 }
        assertEquals(6, front.delay)
    }

    @Test
    fun `eastern spear two hits rows 0 2 4`() {
        val tiles = SolPatterns.spear(2, SolSide.East, open).coords()
        assertTrue((7 to 0) in tiles && (7 to 2) in tiles && (7 to 4) in tiles)
        assertFalse((7 to 1) in tiles || (7 to 3) in tiles)
    }

    @Test
    fun `diagonal spear two from the south west leaves every third diagonal`() {
        val tiles = SolPatterns.spear(2, SolSide.SouthWest, open).coords() - footprintTiles()
        val expected =
            setOf(
                -1 to -1,
                -1 to 2,
                2 to -1,
                -2 to -2,
                -2 to 1,
                1 to -2,
                -3 to -3,
                -3 to 0,
                0 to -3,
            )
        assertEquals(expected, tiles)
    }

    @Test
    fun `diagonal spear one leaves the corner diagonal safe`() {
        val tiles = SolPatterns.spear(1, SolSide.NorthEast, open).coords() - footprintTiles()
        assertEquals(setOf(4 to 5, 5 to 4, 5 to 6, 6 to 5, 6 to 7, 7 to 6), tiles)
    }

    @Test
    fun `shield one spares only the second ring`() {
        val tiles = SolPatterns.shield(1, open).coords()
        assertTrue((2 to -1) in tiles)
        assertFalse((2 to -2) in tiles)
        assertTrue((2 to -3) in tiles)
    }

    @Test
    fun `shield two spares only the third ring`() {
        val tiles = SolPatterns.shield(2, open).coords()
        assertTrue((2 to -2) in tiles)
        assertFalse((2 to -3) in tiles)
        assertTrue((2 to -4) in tiles)
    }

    @Test
    fun `patterns are clipped to the arena`() {
        val arena: (Int, Int) -> Boolean = { x, z -> z >= -3 && x >= -3 && x <= 7 && z <= 7 }
        assertTrue(SolPatterns.spear(1, SolSide.South, arena).all { it.dz >= -3 })
    }

    private fun footprintTiles() = (0..4).flatMap { x -> (0..4).map { z -> x to z } }.toSet()
}
