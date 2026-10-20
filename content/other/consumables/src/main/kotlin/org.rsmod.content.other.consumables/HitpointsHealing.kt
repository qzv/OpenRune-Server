package org.rsmod.content.other.consumables

import org.rsmod.api.player.hook.PlayerHealCapHook
import org.rsmod.api.player.hook.limit
import org.rsmod.game.entity.Player

internal fun boostedHitpoints(
    base: Int,
    current: Int,
    constant: Int,
    percent: Int,
): Int {
    val boost =
        constant + base * percent / 100

    return minOf(
        base + boost,
        current + boost,
    )
}

internal fun restoredHitpoints(
    base: Int,
    current: Int,
    constant: Int,
    percent: Int,
): Int =
    if (current >= base) {
        current
    } else {
        minOf(
            current + constant + base * percent / 100,
            base,
        )
    }

internal fun Iterable<PlayerHealCapHook>.healAmount(
    player: Player,
    hitpoints: Int,
    target: Int,
): Int =
    (
        limit(
            player = player,
            hitpoints = hitpoints,
            maximum = target,
        ) - hitpoints
        ).coerceAtLeast(0)
