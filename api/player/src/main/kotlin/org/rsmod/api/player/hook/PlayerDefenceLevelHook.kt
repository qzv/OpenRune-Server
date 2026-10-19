package org.rsmod.api.player.hook

import org.rsmod.game.entity.Player

public fun interface PlayerDefenceLevelHook {
    /** @return the Defence level used for [player]'s defence roll in place of [level]. */
    public fun modify(player: Player, level: Int): Int
}

public fun Set<PlayerDefenceLevelHook>.applyTo(player: Player, level: Int): Int =
    fold(level) { current, hook -> hook.modify(player, current) }
