package org.rsmod.game.entity.util

import kotlin.math.PI
import kotlin.math.atan2
import org.rsmod.map.CoordGrid

@JvmInline
public value class EntityFaceAngle(public val intValue: Int) {
    public companion object {
        public val ZERO: EntityFaceAngle = EntityFaceAngle(0)
        public val NULL: EntityFaceAngle = EntityFaceAngle(-1)

        private const val TILE_UNITS = 128
        private const val HALF_TURN = 1024
        private const val ANGLE_MASK = 2047

        public fun fromOrNull(nullableAngle: Int?): EntityFaceAngle =
            nullableAngle?.let(::EntityFaceAngle) ?: NULL

        public fun towards(from: CoordGrid, fromSize: Int, to: CoordGrid, toSize: Int): Int {
            val deltaX = (from.x - to.x) * TILE_UNITS + (fromSize - toSize) * TILE_UNITS / 2
            val deltaZ = (from.z - to.z) * TILE_UNITS + (fromSize - toSize) * TILE_UNITS / 2
            val radians = atan2(deltaX.toDouble(), deltaZ.toDouble())
            return (radians * HALF_TURN / PI).toInt() and ANGLE_MASK
        }
    }
}
