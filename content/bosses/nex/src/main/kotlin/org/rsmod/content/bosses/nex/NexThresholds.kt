package org.rsmod.content.bosses.nex

import kotlin.math.min

internal object NexThresholds {
    const val FLOOR_OFFSET = 170

    fun threshold(baseHitpoints: Int, percent: Int): Int = baseHitpoints * percent / 100

    fun floor(baseHitpoints: Int, percent: Int): Int =
        threshold(baseHitpoints, percent) - FLOOR_OFFSET

    fun crossed(hitpoints: Int, baseHitpoints: Int, percent: Int): Boolean =
        hitpoints <= threshold(baseHitpoints, percent)

    fun clamp(hitpoints: Int, damage: Int, floor: Int): Int =
        if (hitpoints <= floor) 0 else min(damage, hitpoints - floor)
}
