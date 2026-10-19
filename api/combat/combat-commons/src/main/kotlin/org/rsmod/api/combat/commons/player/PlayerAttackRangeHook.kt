package org.rsmod.api.combat.commons.player

import org.rsmod.game.entity.Player

public fun interface PlayerAttackRangeHook {
    public fun modify(player: Player, range: Int): Int
}
