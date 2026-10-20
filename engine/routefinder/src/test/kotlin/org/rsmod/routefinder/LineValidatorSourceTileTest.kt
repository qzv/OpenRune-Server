package org.rsmod.routefinder

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class LineValidatorSourceTileTest {
    @Test
    fun `a loc on the source tile blocks line of sight by default`() {
        val flags = field()
        flags.add(SOURCE_X, SOURCE_Z, 0, CollisionFlag.LOC)
        assertFalse(flags.lineOfSight(destX = SOURCE_X - 5))
    }

    @Test
    fun `a walk-blocking source tile loc does not block with the projectile blocker flag`() {
        val flags = field()
        flags.add(SOURCE_X, SOURCE_Z, 0, CollisionFlag.LOC)
        assertTrue(flags.lineOfSight(destX = SOURCE_X - 5, sourceTileFlag = PROJ_BLOCKER))
        assertTrue(flags.lineOfSight(destX = SOURCE_X - 1, sourceTileFlag = PROJ_BLOCKER))
    }

    @Test
    fun `a projectile-blocking source tile loc still blocks with the projectile blocker flag`() {
        val flags = field()
        flags.add(SOURCE_X, SOURCE_Z, 0, CollisionFlag.LOC or CollisionFlag.LOC_PROJ_BLOCKER)
        assertFalse(flags.lineOfSight(destX = SOURCE_X - 5, sourceTileFlag = PROJ_BLOCKER))
    }

    @Test
    fun `a projectile-blocking loc mid-ray still blocks with the projectile blocker flag`() {
        val flags = field()
        flags.add(SOURCE_X, SOURCE_Z, 0, CollisionFlag.LOC)
        flags.add(SOURCE_X - 2, SOURCE_Z, 0, CollisionFlag.LOC or CollisionFlag.LOC_PROJ_BLOCKER)
        assertFalse(flags.lineOfSight(destX = SOURCE_X - 5, sourceTileFlag = PROJ_BLOCKER))
    }

    @Test
    fun `a size 3 destination sees a source held in an ice prison from 10 tiles`() {
        val flags = field()
        flags.add(SOURCE_X, SOURCE_Z, 0, CollisionFlag.LOC)
        for ((dx, dz) in PRISON_ICICLES) {
            flags.add(SOURCE_X + dx, SOURCE_Z + dz, 0, CollisionFlag.LOC)
        }
        val validator = LineValidator(flags)
        val visible =
            validator.hasLineOfSight(
                level = 0,
                srcX = SOURCE_X,
                srcZ = SOURCE_Z,
                destX = SOURCE_X - 2,
                destZ = SOURCE_Z + 10,
                destWidth = 3,
                destLength = 3,
                extraFlag = CollisionFlag.BLOCK_PLAYERS,
                sourceTileFlag = PROJ_BLOCKER,
            )
        assertTrue(visible)
    }

    private fun CollisionFlagMap.lineOfSight(
        destX: Int,
        sourceTileFlag: Int = CollisionFlag.LOC,
    ): Boolean =
        LineValidator(this)
            .hasLineOfSight(
                level = 0,
                srcX = SOURCE_X,
                srcZ = SOURCE_Z,
                destX = destX,
                destZ = SOURCE_Z,
                sourceTileFlag = sourceTileFlag,
            )

    private fun field(): CollisionFlagMap =
        CollisionFlagMap().apply {
            for (x in 3184..3216 step 8) {
                for (z in 3184..3216 step 8) {
                    allocateIfAbsent(x, z, 0)
                }
            }
        }

    private companion object {
        private const val SOURCE_X = 3200
        private const val SOURCE_Z = 3200
        private const val PROJ_BLOCKER = CollisionFlag.LOC_PROJ_BLOCKER

        private val PRISON_ICICLES =
            listOf(1 to 1, 1 to 0, 1 to -1, -1 to 0, -1 to -1, 0 to 1, 0 to -1)
    }
}
