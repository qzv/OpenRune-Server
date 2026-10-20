package org.rsmod.content.bosses.nex

internal enum class NexForm(val npc: String) {
    SoulSplit("npc.nex_soulsplit"),
    Deflect("npc.nex_deflect"),
    Plain("npc.nex"),
}

internal object NexZarosRules {
    private const val ATTACKS_PER_FORM = 4
    private val CYCLE = listOf(NexForm.SoulSplit, NexForm.Deflect, NexForm.Plain)

    fun formChange(attack: Int): NexForm? {
        if (attack < 1 || (attack - 1) % ATTACKS_PER_FORM != 0) return null
        return CYCLE[(attack - 1) / ATTACKS_PER_FORM % CYCLE.size]
    }

    fun soulSplitHeal(damage: Int): Int = damage / 2
}
