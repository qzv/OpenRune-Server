package org.rsmod.content.minigames.colosseum.enemies

import org.rsmod.api.bosses.dsl.Accuracy
import org.rsmod.api.bosses.dsl.MeleeAttackType
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

internal object ColosseumDamage {
    private const val RELENTLESS_VARBIT = "varbit.colosseum_modifier_relentless_stacks_client"
    private val RELENTLESS_BONUS = intArrayOf(0, 1, 3, 6)
    private const val IGNORE_ACCURACY_LEVEL = 3

    fun relentlessLevel(player: Player): Int =
        player.vars[RELENTLESS_VARBIT].coerceIn(0, RELENTLESS_BONUS.lastIndex)

    fun maxHit(player: Player, base: Int): Int = base + RELENTLESS_BONUS[relentlessLevel(player)]

    fun rollMelee(deps: BossDeps, npc: Npc, target: Player, base: Int, type: MeleeAttackType): Int {
        val hits =
            ignoresAccuracy(target) ||
                deps.accuracy.rollMeleeAccuracy(npc, target, type, deps.random)
        return if (hits) deps.random.of(0, maxHit(target, base)) else 0
    }

    private fun ignoresAccuracy(player: Player): Boolean =
        relentlessLevel(player) >= IGNORE_ACCURACY_LEVEL

    fun unchecked(random: GameRandom, base: Int): DamageExpr =
        DamageExpr.Custom { _, target -> random.of(0, maxHit(target, base)) }

    fun roll(random: GameRandom, base: Int, meleeAttackType: MeleeAttackType? = null): DamageExpr =
        Accuracy(
            on = DamageExpr.Custom { _, target -> random.of(0, maxHit(target, base)) },
            miss =
                DamageExpr.Custom { _, target ->
                    if (ignoresAccuracy(target)) random.of(0, maxHit(target, base)) else 0
                },
            meleeAttackType = meleeAttackType,
        )
}
