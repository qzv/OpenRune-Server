package org.rsmod.api.combat.commons.player

import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

public fun interface PlayerMaxHitHook {
    /** @return true when [player]'s attack on [target] always hits its max. */
    public fun forceMaxHit(player: Player, target: PathingEntity): Boolean

    public fun onForcedMaxHit(player: Player, target: PathingEntity) {}
}
