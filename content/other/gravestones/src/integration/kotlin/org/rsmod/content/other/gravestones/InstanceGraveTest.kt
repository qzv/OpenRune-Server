package org.rsmod.content.other.gravestones

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceRegionRotation
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
class InstanceGraveTest {
    @Test
    fun GameTestState
        .`a death in a copy maps back to the template tile and graves at the grave coords`() =
        runInjectedGameTest(InstanceManager::class) { manager ->
            val session = create(manager, InstanceArea.copyRegions(REGION), 1L)
            val death = checkNotNull(manager.resolveCoord(session, DEATH_TILE))

            assertNotEquals(DEATH_TILE, death)
            assertEquals(DEATH_TILE, manager.templateCoord(session, death))
            assertEquals(GRAVE, manager.graveCoords(session))
        }

    @Test
    fun GameTestState.`a multi-region copy maps every region back`() =
        runInjectedGameTest(InstanceManager::class) { manager ->
            val area = InstanceArea.copyRegions(REGION, gridSize = 3)
            val session = create(manager, area, 2L)
            val corner = CoordGrid(DEATH_TILE.x - 64, DEATH_TILE.z + 64, 0)

            for (tile in listOf(DEATH_TILE, corner)) {
                val copy = checkNotNull(manager.resolveCoord(session, tile))
                assertEquals(tile, manager.templateCoord(session, copy))
            }
        }

    @Test
    fun GameTestState.`a rotated copy has no template tile`() =
        runInjectedGameTest(InstanceManager::class) { manager ->
            val area =
                InstanceArea.copyRegions(REGION, rotation = InstanceRegionRotation.CLOCKWISE_90)
            val session = create(manager, area, 3L)

            assertNull(manager.templateCoord(session, CoordGrid(10_300, 3_100, 0)))
        }

    @Test
    fun GameTestState.`without grave coords the grave goes to the exit`() =
        runInjectedGameTest(InstanceManager::class) { manager ->
            val area = InstanceArea.copyRegions(REGION).copy(exitCoord = EXIT)
            val session = create(manager, area, 4L, graveCoords = null)

            assertEquals(EXIT, manager.graveCoords(session))
        }

    private fun GameTestScope.create(
        manager: InstanceManager,
        area: InstanceArea.CopyRegions,
        uuid: Long,
        graveCoords: CoordGrid? = GRAVE,
    ): InstanceSession {
        player.uuid = uuid
        val enter = RegionLocal(0, DEATH_TILE.mx, DEATH_TILE.mz, DEATH_TILE.lx, DEATH_TILE.lz)
        val spec =
            InstanceSpec(
                fee = 0,
                maxPlayers = 1,
                reclaimTicks = 10,
                graceTicks = 10,
                area = area.copy(enterCoord = enter),
                settingsRowId = 0,
                graveCoords = graveCoords,
            )
        val result = manager.create(player, "test", spec, InstanceAccess.Private, currentTick = 0)
        return (result as InstanceManager.Result.Created).session
    }

    private companion object {
        const val REGION = 7216
        val GRAVE = CoordGrid(1802, 9507, 0)
        val DEATH_TILE = CoordGrid(1828, 3104, 0)
        val EXIT = CoordGrid(1804, 9505, 0)
    }
}
