package org.rsmod.content.minigames.colosseum.modifiers

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import org.rsmod.map.CoordGrid

internal object DoomRules {
    private val LIMITS = intArrayOf(15, 10, 5)

    fun limit(level: Int): Int = LIMITS[(level - 1).coerceIn(0, LIMITS.lastIndex)]
}

internal object BlasphemyRules {
    private const val PERCENT_PER_LEVEL = 20
    private const val PERCENT = 100
    private const val MAX_LEVEL = 3

    fun drain(damage: Int, level: Int): Int =
        damage * PERCENT_PER_LEVEL / PERCENT * level.coerceIn(0, MAX_LEVEL)
}

internal object BeeRules {
    const val SIZE = 2
    const val LEG_TICKS = 12
    const val STEP_TICKS = 2
    const val TARGET_SPREAD = 4
    const val RESPAWN_FIRST_STEP = 5
    private const val DOUBLE_DAMAGE_LEVEL = 3

    fun stings(bee: CoordGrid, player: CoordGrid): Boolean =
        bee.level == player.level &&
            player.x - bee.x in 0 until SIZE &&
            player.z - bee.z in 0 until SIZE

    fun startsLeg(age: Int): Boolean = age >= 1 && (age - 1) % LEG_TICKS == 0

    fun steps(age: Int): Boolean = age >= 1 && (age - 1) % STEP_TICKS == 0

    fun stepToward(from: CoordGrid, to: CoordGrid): CoordGrid =
        from.translate((to.x - from.x).sign, (to.z - from.z).sign)

    fun legTarget(player: CoordGrid, dx: Int, dz: Int): CoordGrid = player.translate(dx, dz)

    fun ageAfterRespawn(): Int = 1 - RESPAWN_FIRST_STEP

    fun damage(roll: Int, level: Int): Int = if (level >= DOUBLE_DAMAGE_LEVEL) roll * 2 else roll
}

internal enum class Heading(val dx: Int, val dz: Int) {
    North(0, 1),
    East(1, 0),
    South(0, -1),
    West(-1, 0);

    fun clockwise(turns: Int = 1): Heading = entries[(ordinal + turns).mod(entries.size)]
}

internal object FlareOrbit {
    const val SIDE_STEPS = 4
    const val ARENA_MID_X = 1824
    const val ARENA_MID_Z = 3106
    const val WALK_LEVEL = 3
    const val PRAYER_QUEUE = "queue.colosseum_flare_prayer"
    private val STRAIGHT_TICKS = intArrayOf(2, 2, 1)
    private val CORNER_TICKS = intArrayOf(7, 2, 2)

    fun firstHeading(start: CoordGrid): Heading {
        val east = start.x > ARENA_MID_X
        val north = start.z > ARENA_MID_Z
        return when {
            north && east -> Heading.North
            east -> Heading.East
            !north -> Heading.South
            else -> Heading.West
        }
    }

    fun heading(first: Heading, step: Int): Heading = first.clockwise(step / SIDE_STEPS)

    fun delayAfter(step: Int, level: Int): Int {
        val index = (level - 1).coerceIn(0, STRAIGHT_TICKS.lastIndex)
        return if ((step + 1) % SIDE_STEPS == 0) CORNER_TICKS[index] else STRAIGHT_TICKS[index]
    }

    fun touches(flare: CoordGrid, player: CoordGrid, level: Int, ticksSinceStep: Int): Boolean {
        if (level < WALK_LEVEL && ticksSinceStep == 1) return false
        return flare == player
    }
}

internal object VolatilityRules {
    const val RING_DELAY = 2
    private const val OUTER_LEVEL = 2

    fun radius(level: Int): Int = if (level >= OUTER_LEVEL) 2 else 1

    fun centre(southWest: CoordGrid, size: Int): CoordGrid =
        southWest.translate((size - 1) / 2, (size - 1) / 2)

    fun blast(southWest: CoordGrid, size: Int, radius: Int): List<Pair<CoordGrid, Int>> {
        val centre = centre(southWest, size)
        return (-radius until size + radius).flatMap { dx ->
            (-radius until size + radius).map { dz ->
                val tile = southWest.translate(dx, dz)
                tile to max(abs(tile.x - centre.x), abs(tile.z - centre.z)) * RING_DELAY
            }
        }
    }

    fun inBlast(southWest: CoordGrid, size: Int, radius: Int, tile: CoordGrid): Boolean =
        tile.level == southWest.level &&
            tile.x - southWest.x in -radius until size + radius &&
            tile.z - southWest.z in -radius until size + radius
}

internal object PoolRules {
    private val SCAN_ORDER =
        listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)

    fun neighbours(level: Int): Int = (level - 1).coerceIn(0, 2)

    fun pick(impact: CoordGrid, count: Int, usable: (CoordGrid) -> Boolean): List<CoordGrid> =
        SCAN_ORDER.map { (dx, dz) -> impact.translate(dx, dz) }.filter(usable).take(count)
}
