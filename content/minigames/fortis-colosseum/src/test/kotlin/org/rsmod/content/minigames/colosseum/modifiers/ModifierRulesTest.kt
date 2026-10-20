package org.rsmod.content.minigames.colosseum.modifiers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class ModifierRulesTest {
    @Test
    fun `blasphemy drains a floored share of each hit`() {
        assertEquals(8, BlasphemyRules.drain(40, 1))
        assertEquals(7, BlasphemyRules.drain(35, 1))
        assertEquals(18, BlasphemyRules.drain(47, 2))
        assertEquals(10, BlasphemyRules.drain(25, 2))
        assertEquals(15, BlasphemyRules.drain(25, 3))
        assertEquals(24, BlasphemyRules.drain(41, 3))
        assertEquals(0, BlasphemyRules.drain(4, 1))
        assertEquals(12, BlasphemyRules.drain(34, 2))
        assertEquals(15, BlasphemyRules.drain(29, 3))
        assertEquals(4, BlasphemyRules.drain(13, 2))
        assertEquals(12, BlasphemyRules.drain(24, 3))
    }

    @Test
    fun `bees sting only inside their 2x2 footprint`() {
        val bee = CoordGrid(1830, 3105, 0)
        assertTrue(BeeRules.stings(bee, CoordGrid(1830, 3105, 0)))
        assertTrue(BeeRules.stings(bee, CoordGrid(1831, 3105, 0)))
        assertTrue(BeeRules.stings(bee, CoordGrid(1831, 3106, 0)))
        assertFalse(BeeRules.stings(bee, CoordGrid(1832, 3105, 0)))
        assertFalse(BeeRules.stings(bee, CoordGrid(1829, 3105, 0)))
        assertFalse(BeeRules.stings(bee, CoordGrid(1830, 3104, 0)))
    }

    @Test
    fun `bees step every other tick and pick a heading every twelve`() {
        val steps = (1..24).filter(BeeRules::steps)
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23), steps)
        assertEquals(listOf(1, 13), (1..24).filter(BeeRules::startsLeg))
        assertFalse(BeeRules.steps(0))
    }

    @Test
    fun `bee legs bend once level with the target`() {
        val target = CoordGrid(1818, 3108, 0)
        var position = CoordGrid(1824, 3111, 0)
        val path = mutableListOf<Pair<Int, Int>>()
        repeat(6) {
            val next = BeeRules.stepToward(position, target)
            path += (next.x - position.x) to (next.z - position.z)
            position = next
        }
        assertEquals(listOf(-1 to -1, -1 to -1, -1 to -1, -1 to 0, -1 to 0, -1 to 0), path)
        assertEquals(target, position)
        assertEquals(target, BeeRules.stepToward(target, target))
    }

    @Test
    fun `flares start outward clockwise for their quadrant`() {
        assertEquals(Heading.North, FlareOrbit.firstHeading(CoordGrid(1830, 3112, 0)))
        assertEquals(Heading.West, FlareOrbit.firstHeading(CoordGrid(1819, 3112, 0)))
        assertEquals(Heading.South, FlareOrbit.firstHeading(CoordGrid(1819, 3101, 0)))
        assertEquals(Heading.East, FlareOrbit.firstHeading(CoordGrid(1830, 3101, 0)))
        assertEquals(Heading.North, FlareOrbit.firstHeading(CoordGrid(1826, 3108, 0)))
        assertEquals(Heading.East, FlareOrbit.firstHeading(CoordGrid(1826, 3105, 0)))
        assertEquals(Heading.West, FlareOrbit.firstHeading(CoordGrid(1823, 3108, 0)))
        assertEquals(Heading.South, FlareOrbit.firstHeading(CoordGrid(1823, 3105, 0)))
    }

    @Test
    fun `flare orbit traces its 5x5 square and returns home`() {
        val start = CoordGrid(1830, 3112, 0)
        val first = FlareOrbit.firstHeading(start)
        var position = start
        val visited = mutableSetOf<CoordGrid>()
        repeat(FlareOrbit.SIDE_STEPS * 4) { step ->
            val heading = FlareOrbit.heading(first, step)
            position = position.translate(heading.dx, heading.dz)
            visited += position
            if (step == 3) assertEquals(CoordGrid(1830, 3116, 0), position)
            if (step == 7) assertEquals(CoordGrid(1834, 3116, 0), position)
        }
        assertEquals(start, position)
        assertEquals(16, visited.size)
        assertTrue(visited.all { it.x in 1830..1834 && it.z in 3112..3116 })
    }

    @Test
    fun `flare step cadence per level`() {
        fun ticks(level: Int): List<Int> {
            var tick = 1
            val out = mutableListOf(tick)
            for (step in 0 until 9) {
                tick += FlareOrbit.delayAfter(step, level)
                out += tick
            }
            return out
        }
        assertEquals(listOf(1, 3, 5, 7, 14, 16, 18, 20, 27, 29), ticks(1))
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15, 17, 19), ticks(2))
        assertEquals(listOf(1, 2, 3, 4, 6, 7, 8, 9, 11, 12), ticks(3))
    }

    @Test
    fun `flare contact needs the same tile and skips the tick after a crawl step`() {
        val tile = CoordGrid(1832, 3097, 0)
        assertTrue(FlareOrbit.touches(tile, tile, level = 1, ticksSinceStep = 5))
        assertFalse(FlareOrbit.touches(tile, tile, level = 1, ticksSinceStep = 1))
        assertFalse(FlareOrbit.touches(tile, tile, level = 2, ticksSinceStep = 1))
        assertTrue(FlareOrbit.touches(tile, tile, level = 3, ticksSinceStep = 1))
        assertFalse(FlareOrbit.touches(tile, tile.translateX(1), level = 3, ticksSinceStep = 9))
    }

    @Test
    fun `volatility blast is footprint plus radius with delays from the centre`() {
        assertEquals(1, VolatilityRules.radius(1))
        assertEquals(2, VolatilityRules.radius(2))
        assertEquals(2, VolatilityRules.radius(3))
        val sw = CoordGrid(1815, 3095, 0)
        assertEquals(CoordGrid(1816, 3096, 0), VolatilityRules.centre(sw, 3))
        assertEquals(sw, VolatilityRules.centre(sw, 2))
        assertEquals(CoordGrid(1817, 3097, 0), VolatilityRules.centre(sw, 5))
        val blast = VolatilityRules.blast(sw, 3, 2).toMap()
        assertEquals(49, blast.size)
        assertEquals(0, blast.getValue(CoordGrid(1816, 3096, 0)))
        assertEquals(2, blast.getValue(CoordGrid(1817, 3096, 0)))
        assertEquals(6, blast.getValue(CoordGrid(1813, 3094, 0)))
        assertEquals(81, VolatilityRules.blast(sw, 5, 2).size)
        assertEquals(8, VolatilityRules.blast(sw, 5, 2).maxOf { it.second })
    }

    @Test
    fun `even sized enemies blast one tile west and two east at level one`() {
        val sw = CoordGrid(1835, 3107, 0)
        val blast = VolatilityRules.blast(sw, 2, 1).toMap()
        assertEquals(16, blast.size)
        assertEquals(0, blast.getValue(sw))
        assertEquals(2, blast.getValue(CoordGrid(1834, 3107, 0)))
        assertEquals(4, blast.getValue(CoordGrid(1837, 3109, 0)))
        assertTrue(VolatilityRules.inBlast(sw, 2, 1, CoordGrid(1837, 3106, 0)))
        assertFalse(VolatilityRules.inBlast(sw, 2, 1, CoordGrid(1838, 3107, 0)))
        assertFalse(VolatilityRules.inBlast(sw, 2, 1, CoordGrid(1833, 3107, 0)))
    }

    @Test
    fun `pool neighbours follow the fixed scan order and skip unusable tiles`() {
        val impact = CoordGrid(1822, 3105, 0)
        assertEquals(0, PoolRules.neighbours(1))
        assertEquals(1, PoolRules.neighbours(2))
        assertEquals(2, PoolRules.neighbours(3))
        val blocked = setOf(impact.translate(-1, 0))
        val picked = PoolRules.pick(impact, 2) { it !in blocked }
        assertEquals(listOf(impact.translate(-1, -1), impact.translate(-1, 1)), picked)
    }

    @Test
    fun `bees leg target spreads around the player and respawn delays the first step`() {
        assertEquals(CoordGrid(1835, 3102, 0), BeeRules.legTarget(CoordGrid(1831, 3105, 0), 4, -3))
        var age = BeeRules.ageAfterRespawn()
        var ticks = 0
        while (!BeeRules.steps(age)) {
            age++
            ticks++
        }
        assertEquals(BeeRules.RESPAWN_FIRST_STEP, ticks)
    }
}
