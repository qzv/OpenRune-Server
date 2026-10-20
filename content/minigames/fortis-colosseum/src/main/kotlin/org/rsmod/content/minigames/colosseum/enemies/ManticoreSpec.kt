package org.rsmod.content.minigames.colosseum.enemies

import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.HitType
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.content.minigames.colosseum.ColosseumNpcs

internal enum class ManticoreOrb(
    val type: HitType,
    val spotanim: String,
    val impact: String,
    val maxHit: Int,
) {
    Magic(
        HitType.Magic,
        "spotanim.vfx_manticore_01_projectile_magic_01",
        "spotanim.vfx_manticore_01_projectile_impact_magic_01",
        31,
    ),
    Ranged(
        HitType.Ranged,
        "spotanim.vfx_manticore_01_projectile_ranged_01",
        "spotanim.vfx_manticore_01_projectile_impact_ranged_01",
        36,
    ),
    Melee(
        HitType.Melee,
        "spotanim.vfx_manticore_01_projectile_melee_01",
        "spotanim.vfx_manticore_01_projectile_impact_melee_01",
        31,
    ),
}

internal class ManticoreSpec(private val deps: BossDeps, private val throws: ManticoreThrows) {
    private fun mayhemAtLeast(level: Int): Condition =
        Condition.Custom { _, target -> target != null && target.vars[MANTIMAYHEM_VARBIT] >= level }

    private val pairDelay: Effect = wait(PAIR_DELAY)

    private val throwBlocked: Condition = Condition.Custom { npc, _ -> throws.blocked(npc) }

    private val resetOrbs: Effect = setVarn(ColosseumEnemyVarns.ORBS_SHOWN, 0)

    private fun showOrb(slot: Int): Effect =
        switch(
            ColosseumEnemyVarns.ORBS[slot],
            listOf(Effect.NoOp) +
                ManticoreOrb.entries.map {
                    spotanim(it.spotanim, height = ORB_HEIGHTS[slot], slot = slot + 1)
                },
        )

    private fun revealOrb(slot: Int): Effect =
        sequence(setVarn(ColosseumEnemyVarns.ORBS_SHOWN, slot + 1), showOrb(slot))

    private fun refreshOrbs(): Effect =
        sequence(
            whenever(varnAtLeast(ColosseumEnemyVarns.ORBS_SHOWN, 1), showOrb(0)),
            whenever(varnAtLeast(ColosseumEnemyVarns.ORBS_SHOWN, 2), showOrb(1)),
            whenever(varnAtLeast(ColosseumEnemyVarns.ORBS_SHOWN, 3), showOrb(2)),
        )

    private fun projectileFor(orb: ManticoreOrb, slot: Int, delay: Int): Effect =
        projectile(
            spotanim = orb.spotanim,
            config =
                ProjectileConfig.fixed(
                    startHeight = ORB_HEIGHTS[slot],
                    endHeight = ORB_END_HEIGHT,
                    delay = delay,
                    travel = ORB_TRAVEL - delay,
                    angle = ORB_ANGLE,
                    progress = if (slot == 0) FIRST_ORB_PROGRESS else 0,
                ),
            hit =
                hit {
                    damage(ColosseumDamage.roll(deps.random, orb.maxHit))
                    type(orb.type)
                    spotanim(orb.impact, height = IMPACT_HEIGHT, onLanded = true)
                    onHit(
                        sequence(
                            whenever(!TargetPraying(orb.type), soundTo(ORB_IMPACT_SYNTH)),
                            whenever(
                                mayhemAtLeast(VENOM_LEVEL) and !TargetPraying(orb.type),
                                external(ColosseumEnemyHandlers.VENOM),
                            ),
                        )
                    )
                },
        )

    private fun fireOrb(slot: Int): Effect =
        sequence(
            external(ColosseumEnemyHandlers.CLEAR_ORB, params = slot + 1),
            switch(
                ColosseumEnemyVarns.ORBS[slot],
                listOf(Effect.NoOp) +
                    ManticoreOrb.entries.map { orb ->
                        sequence(
                            projectileFor(orb, slot, delay = 0),
                            whenever(
                                mayhemAtLeast(DOUBLE_LEVEL),
                                projectileFor(orb, slot, delay = DOUBLE_DELAY),
                            ),
                        )
                    },
            ),
            setVarn(ColosseumEnemyVarns.ORBS[slot], 0),
        )

    val spec: BossSpec =
        boss(ColosseumNpcs.MANTICORE) {
            stats(attackRate = 1)
            val throwOrbs =
                ability("throw") {
                    include(whenever(throwBlocked, pairDelay))
                    include(external(ColosseumEnemyHandlers.MARK_THROW))
                    anim("seq.npc_manticore_01_triple_throw")
                    soundTo("synth.colosseum_manticore_throw")
                    include(
                        parallel(
                            fireOrb(0),
                            after(1, fireOrb(1)),
                            after(2, fireOrb(2)),
                            after(3, resetOrbs),
                        )
                    )
                    wait(RECOVERY_TICKS)
                }
            val charge =
                ability("charge") {
                    include(external(ColosseumEnemyHandlers.PICK_ORDER))
                    anim("seq.npc_manticore_01_triple_charge")
                    include(
                        parallel(
                            after(1, revealOrb(0)),
                            after(2, revealOrb(1)),
                            after(3, revealOrb(2)),
                        )
                    )
                    wait(CHARGE_TICKS)
                    forceNext(throwOrbs)
                }
            every(1, refreshOrbs())
            phase("fight") { weightedSelectorRandom { +random(charge, weight = 1) } }
        }

    companion object {
        const val MANTIMAYHEM_VARBIT = "varbit.colosseum_modifier_mantimayhem_stacks_client"
        const val DOUBLE_LEVEL = 1
        const val VENOM_LEVEL = 2
        const val ANY_ORDER_LEVEL = 3

        const val FIRST_CHARGE_DELAY = 7
        const val CHARGE_TICKS = 6
        const val RECOVERY_TICKS = 4
        const val PAIR_DELAY = 5

        private const val ORB_IMPACT_SYNTH = "synth.colosseum_manticore_orb"
        private val ORB_HEIGHTS = intArrayOf(300, 400, 500)
        private const val ORB_END_HEIGHT = 50
        private const val ORB_TRAVEL = 30
        private const val ORB_ANGLE = 127
        private const val FIRST_ORB_PROGRESS = 50
        private const val DOUBLE_DELAY = 5
        private const val IMPACT_HEIGHT = 100
    }
}
