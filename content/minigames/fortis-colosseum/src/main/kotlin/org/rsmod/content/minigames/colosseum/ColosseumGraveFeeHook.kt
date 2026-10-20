package org.rsmod.content.minigames.colosseum

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerGraveFeeHook
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal class ColosseumGraveFeeHook @Inject constructor() : PlayerGraveFeeHook {
    override fun reducedFee(player: Player, deathCoords: CoordGrid): Boolean =
        reducedFee(deathCoords, player.totalWavesCompleted)

    companion object {
        const val REDUCED_FEE_WAVES = 100

        private const val FEE_ZONE_ROW = "dbrow.colosseum_grave_fee_zone"

        fun reducedFee(deathCoords: CoordGrid, wavesCompleted: Int): Boolean {
            if (wavesCompleted >= REDUCED_FEE_WAVES) return false
            val (southWest, northEast) = ColosseumArena.coords(FEE_ZONE_ROW)
            return deathCoords.level in southWest.level..northEast.level &&
                deathCoords.x in southWest.x..northEast.x &&
                deathCoords.z in southWest.z..northEast.z
        }
    }
}
