package org.rsmod.content.bosses.nex

import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.dsl.external
import org.rsmod.api.bosses.spec.BossSpec

internal object NexSpecs {
    const val STANDARD = "nex.standard"
    const val SPECIAL = "nex.special"

    private const val SMOKE = "smoke"
    private const val ATTACK_RATE = 4
    private const val STANDARDS_PER_SPECIAL = 5

    val spec: BossSpec =
        boss(
            "npc.nex_spawning",
            "npc.nex",
            "npc.nex_soulsplit",
            "npc.nex_deflect",
            "npc.nex_dying",
        ) {
            stats(attackRate = ATTACK_RATE)
            val special = ability("special") { include(external(SPECIAL)) }
            val standard = ability("standard") { include(external(STANDARD)) }
            phase(SMOKE) {
                rotationSelector {
                    +then(special)
                    repeat(STANDARDS_PER_SPECIAL) { +then(standard) }
                }
            }
        }
}
