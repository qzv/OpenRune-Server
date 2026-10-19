package org.rsmod.api.death

import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

public fun interface PlayerGraveFeeHook {
    public fun reducedFee(player: Player, deathCoords: CoordGrid): Boolean
}
