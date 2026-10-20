package org.rsmod.content.bosses.nex

import org.rsmod.map.CoordGrid

internal enum class NexForm(val npc: String) {
    SoulSplit("npc.nex_soulsplit"),
    Deflect("npc.nex_deflect"),
    Plain("npc.nex"),
}

internal object NexZarosRules {
    private const val ATTACKS_PER_FORM = 4
    private const val WRATH_RADIUS = 3
    private const val RESPAWN_TICKS = 36
    private const val SPAWN_CYCLE = 3
    private val CYCLE = listOf(NexForm.SoulSplit, NexForm.Deflect, NexForm.Plain)

    fun formChange(attack: Int): NexForm? {
        if (attack < 1 || (attack - 1) % ATTACKS_PER_FORM != 0) return null
        return CYCLE[(attack - 1) / ATTACKS_PER_FORM % CYCLE.size]
    }

    fun soulSplitHeal(damage: Int): Int = damage / 2

    fun inWrath(centre: CoordGrid, tile: CoordGrid): Boolean =
        centre.chebyshevDistance(tile) <= WRATH_RADIUS

    fun respawnTick(now: Int, anchor: Int): Int {
        val earliest = now + RESPAWN_TICKS
        return earliest + Math.floorMod(anchor - earliest, SPAWN_CYCLE)
    }
}
