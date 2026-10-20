package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.max
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.combat.commons.player.combatPlayDefendAnim
import org.rsmod.api.combat.commons.player.queueCombatRetaliate
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.soundSynth
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim

internal data class NexSpell(
    val spotanim: String?,
    val travel: String?,
    val impact: String,
    val impactHeight: Int,
    val impactSynth: String,
    val maxHit: Int,
    val penetration: Int,
    val minEndTime: Int = 0,
    val alwaysHits: Boolean = false,
)

internal class NexCastResult(val impactTick: Int, val damage: Int?)

@Singleton
internal class NexCasts @Inject constructor(private val deps: BossDeps) {
    fun cast(caster: Npc, target: Player, spell: NexSpell): NexCastResult? {
        val endTime = launch(caster, target, spell) ?: return null
        val distance = NexGeometry.footprintDistance(caster.coords, caster.size, target.coords)
        val impactTick =
            if (spell.travel == null) {
                NexGeometry.impactTick(endTime)
            } else {
                NexGeometry.magicImpactTick(distance)
            }
        val delay = impactTick + 1
        target.queueCombatRetaliate(caster, delay)
        val landed =
            spell.alwaysHits || deps.accuracy.rollMagicAccuracy(caster, target, deps.random)
        if (!landed) {
            target.spotanim(SPLASH_SPOTANIM, delay = endTime, height = SPLASH_HEIGHT)
            target.soundSynth(SPLASH_SYNTH, delay = endTime)
            return NexCastResult(impactTick, null)
        }
        target.spotanim(spell.impact, delay = endTime, height = spell.impactHeight)
        target.soundSynth(spell.impactSynth, delay = endTime)
        val rolled = deps.random.of(0, spell.maxHit)
        val hit =
            target.queueHit(
                caster,
                delay,
                HitType.Magic,
                rolled,
                deps.playerHitModifier,
                penetration = spell.penetration,
            )
        deps.worldQueues.add(impactTick) {
            if (target.isValidTarget()) target.combatPlayDefendAnim()
        }
        return NexCastResult(impactTick, hit.damage)
    }

    fun launch(
        caster: Npc,
        target: Player,
        spotanim: String,
        travel: String,
        minEndTime: Int,
    ): Int? {
        val type = ServerCacheManager.getProjectile(travel.asRSCM(RSCMType.PROJANIM)) ?: return null
        val spotanimId = spotanim.asRSCM(RSCMType.SPOTANIM)
        val proj = ProjAnim.fromNpcToPlayer(caster, target, spotanimId, type)
        val endTime = max(minEndTime, proj.endTime)
        val source = NexGeometry.centre(caster.coords, caster.size)
        deps.worldRepo.projAnim(proj.copy(endTime = endTime, startCoord = source))
        return endTime
    }

    private fun launch(caster: Npc, target: Player, spell: NexSpell): Int? {
        val travel = spell.travel ?: return spell.minEndTime
        val spotanim = spell.spotanim ?: return null
        return launch(caster, target, spotanim, travel, spell.minEndTime)
    }

    private companion object {
        private const val SPLASH_SPOTANIM = "spotanim.failedspell_impact"
        private const val SPLASH_HEIGHT = 124
        private const val SPLASH_SYNTH = "synth.spellfail"
    }
}
