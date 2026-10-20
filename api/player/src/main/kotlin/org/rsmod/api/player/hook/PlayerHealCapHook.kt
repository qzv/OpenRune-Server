package org.rsmod.api.player.hook

import org.rsmod.game.entity.Player

public fun interface PlayerHealCapHook {
    /** @return the highest hitpoints level healing may restore [player] to; null for no cap. */
    public fun cap(player: Player): Int?
}

public fun Iterable<PlayerHealCapHook>.limit(player: Player, hitpoints: Int, maximum: Int): Int {
    var limited = maximum
    for (hook in this) {
        val cap = hook.cap(player) ?: continue
        if (hitpoints <= cap) limited = minOf(limited, cap)
    }
    return limited
}
