package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.max
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.combat.commons.player.combatPlayDefendAnim
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.player.queueCombatRetaliate
import org.rsmod.api.enums.NamedEnums.nex_shadow_max_hits
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

@Singleton
internal class NexShadow
@Inject
constructor(
    private val deps: BossDeps,
    private val fights: NexFights,
    private val melee: NexMelee,
    private val locRepo: LocRepository,
) {
    fun standard(nex: Npc, target: Player) {
        val fight = fights.fightFor(nex) ?: return
        if (!melee.attempt(fight, nex, target, MELEE_START_CHANCE, MELEE_START_OUT_OF)) {
            shoot(fight, nex)
        }
    }

    fun special(nex: Npc, target: Player) {
        val fight = fights.fightFor(nex) ?: return
        when (fight.specials.next(NexSpecialCounter.SHADOW)) {
            NexSpecial.Smash -> smash(fight, nex)
            NexSpecial.Embrace -> embrace(fight, nex)
            else -> Unit
        }
    }

    private fun shoot(fight: NexFight, nex: Npc) {
        val type = ServerCacheManager.getProjectile(SHOT_TRAVEL.asRSCM(RSCMType.PROJANIM)) ?: return
        nex.anim(CAST_SEQ)
        val centre = NexGeometry.centre(nex.coords, nex.size)
        deps.worldRepo.soundArea(centre, SHOT_SYNTH, radius = SHOT_SOUND_RADIUS)
        val spotanim = SHOT_SPOTANIM.asRSCM(RSCMType.SPOTANIM)
        for (target in fights.players(fight)) {
            if (NexGeometry.footprintDistance(nex.coords, nex.size, target.coords) > ATTACK_RANGE) {
                continue
            }
            val travel = ProjAnim.fromNpcToPlayer(nex, target, spotanim, type)
            val endTime = max(SHOT_MIN_END_TIME, travel.endTime)
            deps.worldRepo.projAnim(travel.copy(endTime = endTime, startCoord = centre))
            val impactTick = NexGeometry.impactTick(endTime)
            target.queueCombatRetaliate(nex, impactTick + 1)
            target.soundSynth(SHOT_IMPACT_SYNTH, delay = endTime)
            deps.worldQueues.add(impactTick) { land(fight, nex, target) }
        }
    }

    private fun land(fight: NexFight, nex: Npc, target: Player) {
        if (fight.nex !== nex || nex.hitpoints <= 0 || !target.isValidTarget()) return
        val distance = NexGeometry.footprintDistance(nex.coords, nex.size, target.coords)
        val landed = deps.accuracy.rollRangedAccuracy(nex, target, deps.random)
        val damage = if (landed) deps.random.of(0, maxHit(distance)) else 0
        val hit =
            target.queueHit(
                nex,
                IMMEDIATE,
                HitType.Ranged,
                damage,
                deps.playerHitModifier,
                penetration = SHOT_PENETRATION,
            )
        target.combatPlayDefendAnim()
        if (target.vars[PROTECT_MISSILES] == 1 && hit.damage > 0) {
            target.statSub(PRAYER, hit.damage / PRAYER_DRAIN_DIVISOR, 0)
        }
    }

    private fun maxHit(distance: Int): Int =
        nex_shadow_max_hits.getValue(distance.coerceAtMost(FURTHEST_MAX_HIT_DISTANCE))

    private fun smash(fight: NexFight, nex: Npc) {
        fights.shout(fight, nex, SMASH_LINE)
        nex.anim(SMASH_SEQ)
        deps.encounter(nex).nextAttackTick = deps.mapClock.cycle + SMASH_NEXT_ATTACK
        val tiles = fights.players(fight).map { it.coords }.distinct()
        for (tile in tiles) {
            locRepo.add(tile, SMASH_LOC, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
        }
        deps.worldQueues.add(SMASH_DELAY) { explode(fight, nex, tiles) }
    }

    private fun explode(fight: NexFight, nex: Npc, tiles: List<CoordGrid>) {
        val impact = SpotanimType(SMASH_IMPACT.asRSCM(RSCMType.SPOTANIM))
        for (tile in tiles) {
            locRepo.add(
                tile,
                SMASH_REMAINS,
                SMASH_REMAINS_TICKS,
                LocAngle.West,
                LocShape.CentrepieceStraight,
            )
            deps.worldRepo.spotanimMap(impact, tile)
            deps.worldRepo.soundArea(tile, SMASH_SYNTH, radius = SMASH_SOUND_RADIUS)
        }
        if (fight.nex !== nex || nex.hitpoints <= 0) return
        for (player in fights.players(fight)) {
            if (player.coords !in tiles) continue
            val damage = deps.random.of(SMASH_MIN_HIT, SMASH_MAX_HIT)
            player.finishNpcHit(nex, IMMEDIATE, HitType.Typeless, damage, deps.playerHitModifier)
            player.mes(SMASH_MESSAGE)
        }
    }

    private fun embrace(fight: NexFight, nex: Npc) {
        fights.shout(fight, nex, EMBRACE_LINE)
        nex.anim(EMBRACE_SEQ)
        val centre = NexGeometry.centre(nex.coords, nex.size)
        deps.worldRepo.soundArea(centre, EMBRACE_SYNTH, radius = SHOT_SOUND_RADIUS)
        fights.players(fight).forEach { it.mes(EMBRACE_MESSAGE, ChatType.Spam) }
        val darkness = NexDarknessState(deps.mapClock.cycle)
        fight.darkness = darkness
        deps.worldQueues.add(1) { darken(fight, nex, darkness) }
    }

    private fun darken(fight: NexFight, nex: Npc, darkness: NexDarknessState) {
        if (fight.darkness !== darkness) return
        if (fight.nex !== nex || nex.hitpoints <= 0) {
            recede(fight)
            return
        }
        if (deps.mapClock.cycle - darkness.startTick > DARKNESS_TICKS) {
            recede(fight)
            return
        }
        val script = FADE_SCRIPT.asRSCM(RSCMType.CLIENTSCRIPT)
        for (player in fights.players(fight)) {
            val distance = NexGeometry.footprintDistance(nex.coords, nex.size, player.coords)
            player.runClientScript(script, NexDarkness.fade(distance))
            val previous = darkness.counters[player] ?: 0
            val messaged = player in darkness.engulfed
            when (NexDarkness.engulf(previous, distance, messaged)) {
                NexEngulf.Message -> {
                    darkness.engulfed += player
                    player.mes(ENGULF_MESSAGE)
                }
                NexEngulf.Hit -> {
                    val damage = deps.random.of(ENGULF_MIN_HIT, ENGULF_MAX_HIT)
                    val modifier = deps.playerHitModifier
                    player.queueHit(nex, IMMEDIATE, HitType.Typeless, damage, modifier)
                }
                NexEngulf.None -> Unit
            }
            darkness.counters[player] = NexDarkness.step(previous, distance)
        }
        deps.worldQueues.add(1) { darken(fight, nex, darkness) }
    }

    fun recede(fight: NexFight) {
        if (fight.darkness == null) return
        fight.darkness = null
        val script = FADE_SCRIPT.asRSCM(RSCMType.CLIENTSCRIPT)
        for (player in fights.players(fight)) {
            player.mes(RECEDE_MESSAGE, ChatType.Spam)
            player.runClientScript(script, NexDarkness.CLEAR_FADE)
        }
    }

    private companion object {
        private const val ATTACK_RANGE = 10
        private const val IMMEDIATE = 1
        private const val PRAYER = "stat.prayer"
        private const val PROTECT_MISSILES = "varbit.prayer_protectfrommissiles"

        private const val MELEE_START_CHANCE = 21
        private const val MELEE_START_OUT_OF = 39

        private const val CAST_SEQ = "seq.nex_cast_attack"
        private const val SHOT_SPOTANIM = "spotanim.nex_shadow_attack_proj"
        private const val SHOT_TRAVEL = "projanim.nex_shadow_attack"
        private const val SHOT_SYNTH = "synth.nex_shadow_cast"
        private const val SHOT_IMPACT_SYNTH = "synth.nex_shadow_impact"
        private const val SHOT_SOUND_RADIUS = 15
        private const val SHOT_MIN_END_TIME = 60
        private const val SHOT_PENETRATION = 50
        private const val PRAYER_DRAIN_DIVISOR = 2
        private const val FURTHEST_MAX_HIT_DISTANCE = 9

        private const val SMASH_LINE = "Fear the shadow!"
        private const val SMASH_SEQ = "seq.nex_smash_attack"
        private const val SMASH_LOC = "loc.nex_shadow_smash"
        private const val SMASH_REMAINS = "loc.invisible_type8_nonblocking"
        private const val SMASH_IMPACT = "spotanim.shadow_barrage_impact"
        private const val SMASH_SYNTH = "synth.nex_shadow_smash"
        private const val SMASH_SOUND_RADIUS = 4
        private const val SMASH_DELAY = 4
        private const val SMASH_REMAINS_TICKS = 9
        private const val SMASH_NEXT_ATTACK = 8
        private const val SMASH_MIN_HIT = 33
        private const val SMASH_MAX_HIT = 50
        private const val SMASH_MESSAGE = "<col=a53fff>Nex's shadow consumes you!</col>"

        private const val EMBRACE_LINE = "Embrace darkness!"
        private const val EMBRACE_SEQ = "seq.nex_blast_away"
        private const val EMBRACE_SYNTH = "synth.nex_embrace_darkness"
        private const val EMBRACE_MESSAGE = "<col=ff289d>Darkness begins to surround Nex.</col>"
        private const val ENGULF_MESSAGE = "<col=ff289d>The darkness starts to engulf you!</col>"
        private const val RECEDE_MESSAGE = "<col=229628>The darkness recedes.</col>"
        private const val FADE_SCRIPT = "clientscript.[clientscript,script5313]"
        private const val DARKNESS_TICKS = 30
        private const val ENGULF_MIN_HIT = 9
        private const val ENGULF_MAX_HIT = 17
    }
}
