package org.rsmod.content.minigames.colosseum.sol

import org.rsmod.map.CoordGrid

internal object SolRules {
    const val BEAM_LENGTH = 13
    const val BEAM_MIN_DISTANCE = 2
    const val BEAM_IMMEDIATE_DISTANCE = 5
    const val FIRST_SPECIAL_COOLDOWN = 27
    const val SPECIAL_COOLDOWN = 29
    const val SPECIAL_CHANCE_PERCENT = 24
    const val LATE_TRIPLE_PHASE = 3
    const val PARRY_BUFF_TICKS = 5

    private val EARLY_TRIPLE_DAMAGE = intArrayOf(15, 25, 35)
    private val LATE_TRIPLE_DAMAGE = intArrayOf(15, 30, 45)
    private val EARLY_TRIPLE_OFFSETS = intArrayOf(3, 6, 9)
    private val LATE_TRIPLE_OFFSETS = intArrayOf(3, 6, 10)

    fun thresholdReached(hitpoints: Int, base: Int, percent: Int): Boolean =
        hitpoints * 100 <= base.coerceAtLeast(1) * percent

    fun lateTriple(phase: Int): Boolean = phase >= LATE_TRIPLE_PHASE

    fun tripleDamages(phase: Int): IntArray =
        if (lateTriple(phase)) LATE_TRIPLE_DAMAGE else EARLY_TRIPLE_DAMAGE

    fun tripleOffsets(phase: Int): IntArray =
        if (lateTriple(phase)) LATE_TRIPLE_OFFSETS else EARLY_TRIPLE_OFFSETS

    fun parryBuffActive(parryTick: Int, usedTick: Int, now: Int): Boolean =
        usedTick == now || (usedTick < parryTick && now - parryTick in 0..PARRY_BUFF_TICKS)

    fun specialEligible(now: Int, ref: Int, cooldown: Int): Boolean =
        ref >= 0 && now - ref >= cooldown

    fun beamDistance(crystal: CoordGrid, dx: Int, dz: Int, tile: CoordGrid): Int? {
        for (step in 0..BEAM_LENGTH) {
            if (crystal.translate(dx * step, dz * step) == tile) return step
        }
        return null
    }

    fun beamDelay(distance: Int): Int? =
        when {
            distance < BEAM_MIN_DISTANCE -> null
            distance <= BEAM_IMMEDIATE_DISTANCE -> 0
            else -> 1
        }

    fun sandPicks(
        centre: CoordGrid,
        rolls: List<CoordGrid>,
        blocked: (CoordGrid) -> Boolean,
    ): List<CoordGrid> {
        val picks = mutableListOf<CoordGrid>()
        for (tile in listOf(centre) + rolls) {
            if (tile in picks || blocked(tile)) continue
            picks += tile
        }
        return picks
    }
}
