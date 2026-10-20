package org.rsmod.content.bosses.nex

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType

@Singleton
internal class NexMelee @Inject constructor(private val deps: BossDeps) {
    fun attempt(
        fight: NexFight,
        nex: Npc,
        target: Player,
        startChance: Int,
        outOf: Int,
        penetration: Int = MELEE_PENETRATION,
        onHit: ((rolled: Int, dealt: Int) -> Unit)? = null,
    ): Boolean {
        val inReach = NexGeometry.inMeleeReach(nex.coords, nex.size, target.coords)
        val melee = fight.melee.melee(inReach) { deps.random.of(outOf) < startChance }
        if (melee) strike(nex, target, penetration, onHit)
        return melee
    }

    private fun strike(
        nex: Npc,
        target: Player,
        penetration: Int,
        onHit: ((rolled: Int, dealt: Int) -> Unit)?,
    ) {
        nex.anim(MELEE_SEQ)
        val landed =
            deps.accuracy.rollMeleeAccuracy(nex, target, MeleeAttackType.Crush, deps.random)
        val damage = if (landed) deps.random.of(0, MELEE_MAX_HIT) else 0
        val hit =
            target.finishNpcHit(
                nex,
                MELEE_HIT_DELAY,
                HitType.Melee,
                damage,
                deps.playerHitModifier,
                penetration = penetration,
            )
        onHit?.invoke(damage, hit.damage)
    }

    private companion object {
        private const val MELEE_SEQ = "seq.nex_attack"
        private const val MELEE_MAX_HIT = 27
        private const val MELEE_PENETRATION = 20
        private const val MELEE_HIT_DELAY = 1
    }
}
