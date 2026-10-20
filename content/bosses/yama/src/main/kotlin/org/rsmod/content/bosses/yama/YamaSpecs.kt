package org.rsmod.content.bosses.yama

import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.spec.BossSpec

internal object YamaSpecs {
    const val YAMA_NPC = "npc.yama"

    private const val ATTACK_RATE = 8
    private const val SPECIAL_SEQ = "seq.npc_yama01_magic02"
    private const val STOMP_SEQ = "seq.npc_yama01_stomp01"
    private const val FIRE_STOMP_SPOTANIM = "spotanim.vfx_yama_meteor_spotanim01"
    private const val SHADOW_STOMP_SPOTANIM = "spotanim.vfx_yama_shadow_root_spotanim_01"
    private const val FIRE_SPECIAL_SPOTANIM = "spotanim.vfx_yama_flaming_rock_spotanim_01"
    private const val FIRE_SPECIAL_PROJECTILE = "spotanim.vfx_yama_flaming_rock_projectile_01"
    private const val SHADOW_SPECIAL_SPOTANIM = "spotanim.vfx_yama_shadow_spike_spotanim_01"
    private const val SHADOW_SPECIAL_PROJECTILE = "spotanim.vfx_yama_shadow_spike_projectile_01"
    private const val ROTATION_LENGTH = 64
    private const val SECOND_PHASE_HP = 2.0 / 3.0
    private const val THIRD_PHASE_HP = 1.0 / 3.0

    private const val PHASE_ONE = "one"
    private const val PHASE_JUDGE_ONE = "judge_one"
    private const val PHASE_TWO = "two"
    private const val PHASE_JUDGE_TWO = "judge_two"
    private const val PHASE_THREE = YamaPhases.FINAL_NAME

    private const val SPECIAL_PROJANIM = "projanim.yama_special"
    private const val CONTRACT_SPECIAL_PROJANIM = "projanim.yama_special_contract"

    private const val FINAL_ATTACK_RATE = 7
    private const val CONTRACT_STANDARDS_PER_SPECIAL = 2
    private const val FLARE_ORDINAL_CHOICES = 2

    private val BASE: List<BossSpec> =
        YamaElement.entries.flatMap { opening ->
            (0 until FLARE_ORDINAL_CHOICES).map { choice ->
                create(opening, flareIndex(opening, choice), contract = false)
            }
        }

    private val CONTRACT: List<BossSpec> =
        YamaElement.entries.map { opening ->
            create(opening, YamaRotation.NO_FLARE, contract = true)
        }

    private val CONTRACT_FLARES: List<BossSpec> =
        YamaElement.entries.flatMap { opening ->
            (0 until FLARE_ORDINAL_CHOICES).map { choice ->
                create(
                    opening,
                    flareIndex(opening, choice, CONTRACT_STANDARDS_PER_SPECIAL),
                    contract = true,
                )
            }
        }

    val all: List<BossSpec> = BASE + CONTRACT + CONTRACT_FLARES

    fun transitionThreshold(phase: Int, contract: Boolean): Double? =
        when (phase) {
            1 -> if (contract) YamaContracts.FIRST_THRESHOLD else SECOND_PHASE_HP
            2 -> if (contract) YamaContracts.SECOND_THRESHOLD else THIRD_PHASE_HP
            else -> null
        }

    fun forFight(opening: YamaElement, choice: Int, contract: YamaContract?): BossSpec =
        when {
            contract == null -> BASE[opening.ordinal * FLARE_ORDINAL_CHOICES + choice]
            contract.fourFlares -> CONTRACT_FLARES[opening.ordinal * FLARE_ORDINAL_CHOICES + choice]
            else -> CONTRACT[opening.ordinal]
        }

    private fun flareIndex(
        opening: YamaElement,
        choice: Int,
        standards: Int = YamaRotation.STANDARDS_PER_SPECIAL,
    ): Int {
        val shadowSpecials =
            (0 until ROTATION_LENGTH).filter {
                val attack = YamaRotation.attack(opening, it, standards)
                attack.kind == YamaAttackKind.SPECIAL && attack.element == YamaElement.SHADOW
            }
        val firstOrdinal = if (opening == YamaElement.SHADOW) 0 else 1
        return shadowSpecials[firstOrdinal + choice]
    }

    fun specialProjectileSpotanim(element: YamaElement): String =
        if (element == YamaElement.FIRE) FIRE_SPECIAL_PROJECTILE else SHADOW_SPECIAL_PROJECTILE

    fun specialProjectile(contract: Boolean): String =
        if (contract) CONTRACT_SPECIAL_PROJANIM else SPECIAL_PROJANIM

    private fun create(opening: YamaElement, flareIndex: Int, contract: Boolean): BossSpec =
        boss(YAMA_NPC) {
            val rate = if (contract) YamaContracts.ATTACK_INTERVAL else ATTACK_RATE
            val standards =
                if (contract) CONTRACT_STANDARDS_PER_SPECIAL else YamaRotation.STANDARDS_PER_SPECIAL
            val travel = specialProjectile(contract)
            val firstThreshold = checkNotNull(transitionThreshold(1, contract))
            val secondThreshold = checkNotNull(transitionThreshold(2, contract))
            stats(attackRate = rate)

            val fire = standard("fire_standard", YamaElement.FIRE)
            val shadow = standard("shadow_standard", YamaElement.SHADOW)
            val fireSpecial =
                special(
                    "fire_special",
                    YamaElement.FIRE,
                    FIRE_SPECIAL_SPOTANIM,
                    FIRE_SPECIAL_PROJECTILE,
                    travel,
                )
            val shadowSpecial =
                special(
                    "shadow_special",
                    YamaElement.SHADOW,
                    SHADOW_SPECIAL_SPOTANIM,
                    SHADOW_SPECIAL_PROJECTILE,
                    travel,
                )

            val flareCast =
                ability("flare_cast") {
                    anim(YamaStrikes.STANDARD_SEQ)
                    include(external(YamaBoss.FLARE_CAST))
                }
            val finalAttack = ability("final_attack") { include(external(YamaBoss.PHASE_THREE)) }

            val toJudgeOne = judge("to_judge_one", PHASE_TWO)
            val toJudgeTwo = judge("to_judge_two", PHASE_THREE)

            val meteor = stomp("meteor", FIRE_STOMP_SPOTANIM, YamaBoss.METEOR)
            val shadowStomp = stomp("shadow_stomp", SHADOW_STOMP_SPOTANIM, YamaBoss.SHADOW_STOMP)

            phase(PHASE_ONE, lockMovement = true) {
                rotation(
                    opening,
                    fire,
                    shadow,
                    fireSpecial,
                    shadowSpecial,
                    flareCast,
                    flareIndex,
                    standards,
                )
            }
            phase(
                PHASE_JUDGE_ONE,
                entryHp = firstThreshold,
                lockMovement = true,
                entryOnAttack = true,
            ) {
                entry = toJudgeOne.name
            }
            phase(PHASE_TWO, lockMovement = true) {
                rotation(
                    opening,
                    fire,
                    shadow,
                    meteor,
                    shadowStomp,
                    flareCast,
                    flareIndex,
                    standards,
                )
            }
            phase(
                PHASE_JUDGE_TWO,
                entryHp = secondThreshold,
                lockMovement = true,
                entryOnAttack = true,
            ) {
                entry = toJudgeTwo.name
            }
            val finalRate = rate.coerceAtMost(FINAL_ATTACK_RATE)
            phase(PHASE_THREE, lockMovement = true, attackRate = finalRate) {
                rotationSelector {
                    repeat(ROTATION_LENGTH) { +then(finalAttack) }
                }
            }
        }

    private fun PhaseBuilder.rotation(
        opening: YamaElement,
        fire: AbilityRef,
        shadow: AbilityRef,
        fireSpecial: AbilityRef,
        shadowSpecial: AbilityRef,
        flareCast: AbilityRef,
        flareIndex: Int,
        standards: Int,
    ) {
        rotationSelector {
            for (index in 0 until ROTATION_LENGTH) {
                val attack = YamaRotation.attack(opening, index, standards, flareIndex)
                val fireAttack = attack.element == YamaElement.FIRE
                val special = attack.kind == YamaAttackKind.SPECIAL
                val ability =
                    when {
                        index == flareIndex -> flareCast
                        special -> if (fireAttack) fireSpecial else shadowSpecial
                        else -> if (fireAttack) fire else shadow
                    }
                +then(ability)
            }
        }
    }

    private fun BossSpecBuilder.stomp(name: String, spotanim: String, handler: String): AbilityRef =
        ability(name) {
            anim(STOMP_SEQ)
            spotanim(spotanim)
            include(external(handler))
        }

    private fun BossSpecBuilder.judge(name: String, nextPhase: String): AbilityRef =
        ability(name) { include(external(YamaBoss.JUDGE, params = nextPhase)) }

    private fun BossSpecBuilder.standard(name: String, element: YamaElement): AbilityRef =
        ability(name) { include(external(YamaBoss.STANDARD, params = element)) }

    private fun BossSpecBuilder.special(
        name: String,
        element: YamaElement,
        spotanim: String,
        projectileSpotanim: String,
        travel: String,
    ): AbilityRef =
        ability(name) {
            anim(SPECIAL_SEQ)
            spotanim(spotanim)
            include(external(YamaBoss.SPECIAL_START, params = element))
            projectile(spotanim = projectileSpotanim, travel = travel)
        }
}
