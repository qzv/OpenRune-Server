package org.rsmod.content.bosses.nex

import org.rsmod.api.bosses.dsl.BossSpecBuilder
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.dsl.external
import org.rsmod.api.bosses.spec.BossSpec

internal object NexSpecs {
    const val SMOKE_STANDARD = "nex.smoke_standard"
    const val SMOKE_SPECIAL = "nex.smoke_special"
    const val SHADOW_STANDARD = "nex.shadow_standard"
    const val SHADOW_SPECIAL = "nex.shadow_special"
    const val BLOOD_STANDARD = "nex.blood_standard"
    const val BLOOD_SPECIAL = "nex.blood_special"
    const val ICE_STANDARD = "nex.ice_standard"
    const val ICE_SPECIAL = "nex.ice_special"
    const val ZAROS_STANDARD = "nex.zaros_standard"
    const val MAGE_ATTACK = "nex.mage_attack"
    const val REAVER_ATTACK = "nex.reaver_attack"

    private const val SMOKE = "smoke"
    private const val SHADOW = "shadow"
    private const val BLOOD = "blood"
    private const val ICE = "ice"
    private const val ZAROS = "zaros"
    private const val ATTACK_RATE = 4
    private const val MAGE_ATTACK_RATE = 5
    private const val REAVER_ATTACK_RATE = 5
    private const val STANDARDS_PER_SPECIAL = 5

    val PHASES: List<String> = listOf(SMOKE, SHADOW, BLOOD, ICE, ZAROS)

    val spec: BossSpec =
        boss(
            "npc.nex_spawning",
            "npc.nex",
            "npc.nex_soulsplit",
            "npc.nex_deflect",
            "npc.nex_dying",
        ) {
            stats(attackRate = ATTACK_RATE)
            rotationPhase(SMOKE, SMOKE_SPECIAL, SMOKE_STANDARD)
            rotationPhase(SHADOW, SHADOW_SPECIAL, SHADOW_STANDARD)
            rotationPhase(BLOOD, BLOOD_SPECIAL, BLOOD_STANDARD)
            rotationPhase(ICE, ICE_SPECIAL, ICE_STANDARD)
            val zarosStandard = ability("zaros_standard") { include(external(ZAROS_STANDARD)) }
            phase(ZAROS) { rotationSelector { +then(zarosStandard) } }
        }

    val MAGES: Map<String, BossSpec> =
        listOf(
                "npc.nex_smokemage",
                "npc.nex_shadowmage",
                "npc.nex_bloodmage",
                "npc.nex_icemage",
            )
            .associateWith(::mage)

    val REAVER: BossSpec =
        boss("npc.nex_prison_blood_reaver_boss") {
            stats(attackRate = REAVER_ATTACK_RATE)
            val rush = ability("blood_rush") { include(external(REAVER_ATTACK)) }
            phase("fight") { rotationSelector { +then(rush) } }
        }

    private fun BossSpecBuilder.rotationPhase(name: String, special: String, standard: String) {
        val specialAbility = ability("${name}_special") { include(external(special)) }
        val standardAbility = ability("${name}_standard") { include(external(standard)) }
        phase(name) {
            rotationSelector {
                +then(specialAbility)
                repeat(STANDARDS_PER_SPECIAL) { +then(standardAbility) }
            }
        }
    }

    private fun mage(npc: String): BossSpec =
        boss(npc) {
            stats(attackRate = MAGE_ATTACK_RATE)
            val barrage = ability("barrage") { include(external(MAGE_ATTACK)) }
            phase("fight") { rotationSelector { +then(barrage) } }
        }
}
