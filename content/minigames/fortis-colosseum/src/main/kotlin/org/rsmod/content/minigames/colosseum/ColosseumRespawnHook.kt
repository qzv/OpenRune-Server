package org.rsmod.content.minigames.colosseum

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.random.GameRandom
import org.rsmod.api.table.InstanceSettingsRow
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal class ColosseumRespawnHook
@Inject
constructor(
    private val instances: InstanceManager,
    private val run: ColosseumRun,
    private val random: GameRandom,
) : PlayerRespawnHook {
    private val key by lazy { InstanceSettingsRow.getRow(ColosseumInstance.SETTINGS_ROW).key }

    override fun respawnCoords(player: Player): CoordGrid? {
        if (instances.sessionForPlayer(player)?.key != key) return null
        run.clear(player)
        return ColosseumArena.randomLobbyExit(random)
    }
}
