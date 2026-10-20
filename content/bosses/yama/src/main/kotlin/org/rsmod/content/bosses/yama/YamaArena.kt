package org.rsmod.content.bosses.yama

import org.rsmod.api.table.YamaArenaRow

internal enum class WaveLayer(val spotanim: String) {
    SOUTH_WEST("spotanim.vfx_shadow_wall_02"),
    SOUTH_EAST("spotanim.vfx_shadow_wall_03");

    val other: WaveLayer
        get() = if (this == SOUTH_WEST) SOUTH_EAST else SOUTH_WEST

    fun delay(x: Int, z: Int): Int =
        if (this == SOUTH_WEST) {
            WAVE_STEP * (x + z) - SOUTH_WEST_BASE
        } else {
            WAVE_STEP * (z - x) - SOUTH_EAST_BASE
        }

    private companion object {
        private const val WAVE_STEP = 15
        private const val SOUTH_WEST_BASE = 173280
        private const val SOUTH_EAST_BASE = 128220
    }
}

internal class StreakRow(val z: Int, val eastStart: Int, val westStart: Int, val eastOnly: Boolean)

internal object YamaArena {
    const val CYCLES_PER_TICK = 30

    val WAVE_TILES: List<Pair<Int, Int>> by lazy {
        YamaArenaRow.all().flatMap { row ->
            row.waveRuns.chunked(2).flatMap { (start, end) ->
                (start..end).map { x -> x to row.z }
            }
        }
    }

    private val WAVE_TILE_SET: Set<Pair<Int, Int>> by lazy { WAVE_TILES.toSet() }

    val STREAK_ROWS: List<StreakRow> by lazy {
        YamaArenaRow.all().mapNotNull { row ->
            val east = row.streakEastStart ?: return@mapNotNull null
            val west = row.streakWestStart ?: return@mapNotNull null
            StreakRow(row.z, east, west, row.streakEastOnly)
        }
    }

    fun isWaveTile(x: Int, z: Int): Boolean = (x to z) in WAVE_TILE_SET

    fun waveArrival(start: Int, layer: WaveLayer, x: Int, z: Int): Int =
        start + Math.floorDiv(layer.delay(x, z), CYCLES_PER_TICK)
}
