package org.rsmod.content.bosses.nex

import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.dsl.external
import org.rsmod.api.bosses.spec.BossSpec

internal object NexSpecs {
    const val STANDARD = "nex.standard"
    const val SPECIAL = "nex.special"
    const val MAGE_ATTACK = "nex.mage_attack"
    private const val FUMUS = "npc.nex_smokemage"

    private const val SMOKE = "smoke"
    private const val SHADOW = "shadow"
    private const val ATTACK_RATE = 4
    private const val MAGE_ATTACK_RATE = 5
    private const val STANDARDS_PER_SPECIAL = 5

    val PHASES: List<String> = listOf(SMOKE, SHADOW)

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
            phase(SHADOW) {}
        }

    val mages: Map<String, BossSpec> = mapOf(FUMUS to mage(FUMUS))

    private fun mage(npc: String): BossSpec =
        boss(npc) {
            stats(attackRate = MAGE_ATTACK_RATE)
            val barrage = ability("barrage") { include(external(MAGE_ATTACK)) }
            phase("fight") { rotationSelector { +then(barrage) } }
        }
}
