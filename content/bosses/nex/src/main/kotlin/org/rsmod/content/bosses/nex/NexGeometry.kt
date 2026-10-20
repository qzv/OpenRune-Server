package org.rsmod.content.bosses.nex

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import org.rsmod.map.CoordGrid

internal object NexGeometry {
    private const val CYCLES_PER_TICK = 30
    private const val HALF_TICK = 15
    private const val DRAG_OFFSET = 2
    private const val MAGIC_MIN_IMPACT = 2
    private const val MAGIC_TILES_PER_TICK = 3

    fun footprintDistance(sw: CoordGrid, size: Int, tile: CoordGrid): Int {
        val dx = max(0, max(sw.x - tile.x, tile.x - (sw.x + size - 1)))
        val dz = max(0, max(sw.z - tile.z, tile.z - (sw.z + size - 1)))
        return max(dx, dz)
    }

    fun footprint(sw: CoordGrid, size: Int): List<CoordGrid> =
        (0 until size).flatMap { x -> (0 until size).map { z -> sw.translate(x, z) } }

    fun ring(sw: CoordGrid, size: Int): List<CoordGrid> =
        footprint(sw.translate(-1, -1), size + 2).filter { footprintDistance(sw, size, it) == 1 }

    fun inMeleeReach(sw: CoordGrid, size: Int, tile: CoordGrid): Boolean =
        footprintDistance(sw, size, tile) == 1

    fun centre(sw: CoordGrid, size: Int): CoordGrid = sw.translate(size / 2, size / 2)

    fun impactTick(endTime: Int): Int = (endTime + HALF_TICK) / CYCLES_PER_TICK

    fun magicImpactTick(distance: Int): Int = MAGIC_MIN_IMPACT + distance / MAGIC_TILES_PER_TICK

    fun dragTile(centre: CoordGrid, tile: CoordGrid): CoordGrid {
        val dx = tile.x - centre.x
        val dz = tile.z - centre.z
        return if (abs(dz) >= abs(dx)) {
            centre.translate(0, DRAG_OFFSET * dz.sign)
        } else {
            centre.translate(DRAG_OFFSET * dx.sign, 0)
        }
    }
}
