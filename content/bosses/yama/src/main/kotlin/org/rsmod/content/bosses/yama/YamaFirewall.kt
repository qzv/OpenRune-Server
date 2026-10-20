package org.rsmod.content.bosses.yama

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

@Singleton
internal class YamaFirewall
@Inject
constructor(private val deps: BossDeps, private val manager: InstanceManager) {
    private val walls = HashMap<InstanceId, List<LocInfo>>()

    fun raise(session: InstanceSession) {
        walls[session.id] =
            TEMPLATE_COORDS.mapNotNull { template ->
                val coords = manager.resolveCoord(session, template) ?: return@mapNotNull null
                val angle = LocAngle[deps.random.of(ROTATIONS)]
                val shape = LocShape.CentrepieceStraight
                deps.locRepo.add(coords, FIREWALL_LOC, Int.MAX_VALUE, angle, shape)
            }
    }

    fun lower(session: InstanceSession) {
        val wall = walls.remove(session.id) ?: return
        open(wall)
        deps.worldQueues.add(SETTLE_DELAY) { open(wall) }
    }

    private fun open(wall: List<LocInfo>) {
        for (loc in wall) {
            val angle = LocAngle[deps.random.of(ROTATIONS)]
            deps.locRepo.add(loc.coords, OPEN_LOC, Int.MAX_VALUE, angle, loc.shape)
        }
    }

    fun forget(id: InstanceId) {
        walls.remove(id)
    }

    private companion object {
        private const val FIREWALL_LOC = "loc.yama_firewall"
        private const val OPEN_LOC = "loc.invisible_type8_nonblocking"
        private const val ROTATIONS = 4
        private const val SETTLE_DELAY = 5
        private val TEMPLATE_COORDS =
            listOf(CoordGrid(1502, 10067), CoordGrid(1503, 10067), CoordGrid(1504, 10067))
    }
}
