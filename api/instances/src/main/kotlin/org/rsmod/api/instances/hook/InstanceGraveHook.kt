package org.rsmod.api.instances.hook

import jakarta.inject.Inject
import org.rsmod.api.death.DeathGrave
import org.rsmod.api.death.PlayerInstanceGraveHook
import org.rsmod.api.instances.InstanceManager
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal class InstanceGraveHook @Inject constructor(private val manager: InstanceManager) :
    PlayerInstanceGraveHook {
    override fun grave(player: Player, deathCoords: CoordGrid): DeathGrave? {
        val session = manager.sessionForPlayer(player) ?: return null
        val coords = manager.graveCoords(session) ?: return null
        return DeathGrave(
            coords = coords,
            deathCoords = manager.templateCoord(session, deathCoords),
            keepUntradeables = session.spec.keepUntradeablesOnDeath,
        )
    }
}
