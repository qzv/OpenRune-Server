package org.rsmod.content.bosses.nex

import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.spec.BossSpec

internal object NexSpecs {
    private const val ATTACK_RATE = 4

    val spec: BossSpec =
        boss(
            "npc.nex_spawning",
            "npc.nex",
            "npc.nex_soulsplit",
            "npc.nex_deflect",
            "npc.nex_dying",
        ) {
            stats(attackRate = ATTACK_RATE)
            ability("smoke_cast") { anim("seq.nex_cast_attack") }
            phase("smoke") {}
        }
}
