package org.rsmod.content.bosses.nex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.rsmod.map.CoordGrid

class NexGeometryTest {
    private val sw = CoordGrid(2935, 5194, 0)

    @Test
    fun `diagonal corner tiles are in melee reach`() {
        assertTrue(NexGeometry.inMeleeReach(sw, 3, CoordGrid(2934, 5193, 0)))
        assertTrue(NexGeometry.inMeleeReach(sw, 3, CoordGrid(2938, 5197, 0)))
    }

    @Test
    fun `cardinal edge tiles are in melee reach`() {
        assertTrue(NexGeometry.inMeleeReach(sw, 3, CoordGrid(2936, 5193, 0)))
        assertTrue(NexGeometry.inMeleeReach(sw, 3, CoordGrid(2938, 5195, 0)))
    }

    @Test
    fun `tiles under or two away are out of melee reach`() {
        assertFalse(NexGeometry.inMeleeReach(sw, 3, CoordGrid(2936, 5195, 0)))
        assertFalse(NexGeometry.inMeleeReach(sw, 3, CoordGrid(2933, 5194, 0)))
        assertFalse(NexGeometry.inMeleeReach(sw, 3, CoordGrid(2939, 5198, 0)))
    }

    @Test
    fun `footprint distance is measured from the nearest footprint tile`() {
        assertEquals(0, NexGeometry.footprintDistance(sw, 3, CoordGrid(2937, 5196, 0)))
        assertEquals(10, NexGeometry.footprintDistance(sw, 3, CoordGrid(2925, 5200, 0)))
    }

    @ParameterizedTest
    @CsvSource(
        "60,2",
        "66,2",
        "76,3",
        "86,3",
        "96,3",
        "106,4",
        "116,4",
        "126,4",
        "136,5",
        "146,5",
    )
    fun `hits land on the nearest tick to the flight end`(endTime: Int, tick: Int) {
        assertEquals(tick, NexGeometry.impactTick(endTime))
    }

    @Test
    fun `drag lands next to the footprint edge facing the player`() {
        val centre = CoordGrid(2914, 5205, 0)
        val north = NexGeometry.dragTile(centre, CoordGrid(2915, 5211, 0))
        val west = NexGeometry.dragTile(centre, CoordGrid(2908, 5206, 0))
        assertEquals(CoordGrid(2914, 5207, 0), north)
        assertEquals(CoordGrid(2912, 5205, 0), west)
    }

    @Test
    fun `magic hits land two ticks out plus one per three tiles`() {
        for (distance in 0..12) {
            val endTime = maxOf(60, 46 + 10 * distance)
            assertEquals(NexGeometry.impactTick(endTime), NexGeometry.magicImpactTick(distance))
        }
        assertEquals(4, NexGeometry.magicImpactTick(6))
        assertEquals(3, NexGeometry.magicImpactTick(5))
        assertEquals(2, NexGeometry.magicImpactTick(2))
    }

    @Test
    fun `footprint gap measures between the nearest tiles of two npcs`() {
        val nex = CoordGrid(2935, 5207, 0)
        assertEquals(2, NexGeometry.footprintGap(nex, 3, CoordGrid(2935, 5204, 0), 2))
        assertEquals(0, NexGeometry.footprintGap(nex, 3, CoordGrid(2936, 5207, 0), 2))
        assertEquals(1, NexGeometry.footprintGap(nex, 3, CoordGrid(2938, 5209, 0), 2))
        assertEquals(8, NexGeometry.footprintGap(nex, 3, CoordGrid(2945, 5208, 0), 2))
        assertEquals(8, NexGeometry.footprintGap(nex, 3, CoordGrid(2926, 5207, 0), 2))
        assertEquals(9, NexGeometry.footprintGap(nex, 3, CoordGrid(2925, 5207, 0), 2))
    }

    @Test
    fun `contain covers the nine Nex tiles and the sixteen tiles around them`() {
        val nex = CoordGrid(2918, 5191, 0)
        val footprint = NexGeometry.footprint(nex, 3)
        val ring = NexGeometry.ring(nex, 3)
        assertEquals(9, footprint.size)
        assertEquals(16, ring.size)
        assertTrue(ring.none { it in footprint })
        assertTrue(CoordGrid(2917, 5190, 0) in ring)
        assertTrue(CoordGrid(2921, 5194, 0) in ring)
        assertFalse(CoordGrid(2922, 5194, 0) in ring)
    }

    @Test
    fun `an ice prison rings the eight tiles around the player`() {
        val centre = CoordGrid(2930, 5192, 0)
        val ring = NexGeometry.ring(centre, 1)
        assertEquals(8, ring.size)
        assertFalse(centre in ring)
        assertTrue(CoordGrid(2929, 5191, 0) in ring)
        assertTrue(CoordGrid(2931, 5193, 0) in ring)
    }
}
