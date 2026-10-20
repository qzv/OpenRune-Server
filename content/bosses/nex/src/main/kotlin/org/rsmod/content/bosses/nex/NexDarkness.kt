package org.rsmod.content.bosses.nex

import kotlin.math.max
import kotlin.math.min
import org.rsmod.game.entity.Player

internal enum class NexEngulf {
    None,
    Message,
    Hit,
}

internal object NexDarkness {
    const val ENGULF_TICKS = 8
    const val CLEAR_FADE = 255
    private const val CLOSE_DISTANCE = 1
    private const val FADE_PER_TILE = 20
    private const val MIN_FADE = 50
    private const val MAX_FADE = 200

    fun fade(distance: Int): Int =
        (FADE_PER_TILE * distance + FADE_PER_TILE).coerceIn(MIN_FADE, MAX_FADE)

    fun step(counter: Int, distance: Int): Int =
        if (distance <= CLOSE_DISTANCE) min(ENGULF_TICKS, counter + 1) else max(0, counter - 1)

    fun engulf(previous: Int, distance: Int, messaged: Boolean): NexEngulf =
        when {
            distance > CLOSE_DISTANCE -> NexEngulf.None
            previous >= ENGULF_TICKS -> NexEngulf.Hit
            previous + 1 >= ENGULF_TICKS && !messaged -> NexEngulf.Message
            else -> NexEngulf.None
        }
}

internal class NexDarknessState(val startTick: Int) {
    val counters: MutableMap<Player, Int> = HashMap()
    val engulfed: MutableSet<Player> = HashSet()
}
