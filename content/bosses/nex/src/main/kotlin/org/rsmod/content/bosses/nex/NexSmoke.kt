package org.rsmod.content.bosses.nex

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.Angles
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.bosses.runtime.suppressAttacks
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.disableOverheadPrayers
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.statSub
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.EntityExactMove
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.map.CoordGrid

@Singleton
internal class NexSmoke
@Inject
constructor(
    private val deps: BossDeps,
    private val fights: NexFights,
    private val casts: NexCasts,
    private val melee: NexMelee,
    private val bonuses: WornBonuses,
    private val interactions: AiPlayerInteractions,
) {
    fun standard(nex: Npc, target: Player) {
        val fight = fights.fightFor(nex) ?: return
        if (!melee.attempt(fight, nex, target, MELEE_START_CHANCE, MELEE_START_OUT_OF)) {
            cast(fight, nex)
        }
    }

    fun special(nex: Npc, target: Player) {
        val fight = fights.fightFor(nex) ?: return
        when (fight.specials.next(NexSpecialCounter.SMOKE)) {
            NexSpecial.Virus -> virus(fight, nex, target)
            NexSpecial.Dash -> dash(fight, nex, target)
            else -> Unit
        }
    }

    private fun cast(fight: NexFight, nex: Npc) {
        nex.anim(CAST_SEQ)
        val centre = NexGeometry.centre(nex.coords, nex.size)
        deps.worldRepo.soundArea(centre, CAST_SYNTH, radius = CAST_SOUND_RADIUS)
        val targets =
            fights.players(fight).filter {
                NexGeometry.footprintDistance(nex.coords, nex.size, it.coords) <= ATTACK_RANGE
            }
        val dragged = dragCandidate(nex, targets)
        for (target in targets) {
            val result = casts.cast(nex, target, SMOKE_SPELL) ?: continue
            val damage = result.damage
            if (damage != null && damage > 0 && deps.random.of(POISON_OUT_OF) < POISON_CHANCE) {
                deps.worldQueues.add(result.impactTick) { poison(target) }
            }
            if (target === dragged) {
                deps.worldQueues.add(result.impactTick) { drag(fight, nex, target) }
            }
        }
    }

    private fun poison(target: Player) {
        if (target.isValidTarget()) CombatEffects.poison(target, POISON_DAMAGE)
    }

    private fun dragCandidate(nex: Npc, targets: List<Player>): Player? {
        val near =
            targets.filter {
                NexGeometry.footprintDistance(nex.coords, nex.size, it.coords) in 1..DRAG_RANGE
            }
        val candidate = deps.random.pickOrNull(near) ?: return null
        val outOf = if (candidate.vars[PROTECT_MAGIC] == 1) DRAG_PRAYING_OUT_OF else DRAG_OUT_OF
        return candidate.takeIf { deps.random.of(outOf) == 0 }
    }

    private fun drag(fight: NexFight, nex: Npc, target: Player) {
        if (fight.nex !== nex || nex.hitpoints <= 0 || !target.isValidTarget()) return
        val centre = NexGeometry.centre(nex.coords, nex.size)
        val dest = NexGeometry.dragTile(centre, target.coords)
        if (deps.collision.isWalkBlocked(dest)) return
        target.mes(DRAG_MESSAGE)
        PathingEntityCommon.teleport(target, deps.collision, dest)
        deps.worldQueues.add(1) {
            if (!target.isValidTarget()) return@add
            target.disableOverheadPrayers()
            target.mes(INJURED_MESSAGE)
        }
    }

    private fun virus(fight: NexFight, nex: Npc, target: Player) {
        fights.shout(fight, nex, VIRUS_LINE)
        nex.anim(VIRUS_SEQ)
        val centre = NexGeometry.centre(nex.coords, nex.size)
        deps.worldRepo.soundArea(centre, VIRUS_SYNTH, radius = CAST_SOUND_RADIUS)
        deps.worldQueues.add(VIRUS_MESSAGE_DELAY) {
            if (target.isValidTarget()) target.mes(VIRUS_START_MESSAGE)
        }
        for (delay in COUGH_DELAYS) deps.worldQueues.add(delay) { cough(target) }
        deps.worldQueues.add(VIRUS_END_DELAY) {
            if (target.isValidTarget()) target.mes(VIRUS_END_MESSAGE)
        }
    }

    private fun cough(target: Player) {
        if (!target.isValidTarget()) return
        target.say(COUGH)
        target.spotanim(COUGH_SPOTANIM, height = COUGH_HEIGHT)
        target.soundSynth(COUGH_SYNTH)
        target.statSub(PRAYER, COUGH_DRAIN, 0)
        target.statSub(highestAttackStat(target), COUGH_DRAIN, 0)
    }

    private fun highestAttackStat(player: Player): String {
        val bonuses = bonuses.calculate(player)
        val melee = maxOf(bonuses.offStab, bonuses.offSlash, bonuses.offCrush)
        return NexDrains.drainedStat(melee, bonuses.offRange, bonuses.offMagic)
    }

    private fun dash(fight: NexFight, nex: Npc, target: Player) {
        val home = fight.home ?: return
        val direction = deps.random.pick(DASH_AISLES)
        val aisle = home.translate(direction.xOff * DASH_LENGTH, direction.zOff * DASH_LENGTH)
        fights.shout(fight, nex, DASH_LINE)
        nex.anim(DASH_SEQ, delay = DASH_SEQ_DELAY)
        nex.movementLocked = true
        deps.suppressAttacks(nex, DASH_BUSY_TICKS)
        deps.encounter(nex).nextAttackTick = deps.mapClock.cycle + DASH_NEXT_ATTACK
        deps.worldQueues.add(DASH_LEAP_DELAY) {
            if (fight.nex !== nex || nex.hitpoints <= 0) return@add
            nex.telejump(deps.collision, aisle)
            fights.shout(fight, nex, DASH_LEAP_LINE)
        }
        deps.worldQueues.add(DASH_RETURN_DELAY) {
            if (fight.nex !== nex || nex.hitpoints <= 0) return@add
            nex.teleport(deps.collision, home)
            nex.pendingExactMove = returnMove(aisle, home)
            nex.movementLocked = false
            if (target.isValidTarget()) {
                nex.facePlayer(target)
                nex.apPlayer2(target, interactions)
            }
        }
        deps.worldQueues.add(DASH_RESET_DELAY) {
            if (fight.nex === nex && nex.hitpoints > 0) nex.resetAnim()
        }
    }

    private fun returnMove(from: CoordGrid, to: CoordGrid): EntityExactMove =
        EntityExactMove(
            deltaX1 = from.x - to.x,
            deltaZ1 = from.z - to.z,
            deltaX2 = 0,
            deltaZ2 = 0,
            clientDelay1 = 0,
            clientDelay2 = DASH_RETURN_CYCLES,
            direction = Angles.bearing(from, to),
        )

    private companion object {
        private const val ATTACK_RANGE = 10
        private const val CAST_SEQ = "seq.nex_cast_attack"
        private const val CAST_SYNTH = "synth.nex_smoke_cast"
        private const val CAST_SOUND_RADIUS = 15
        private val SMOKE_SPELL =
            NexSpell(
                spotanim = "spotanim.nex_smoke_attack_proj",
                travel = "projanim.nex_smoke_attack",
                impact = "spotanim.nex_smoke_attack_impact",
                impactHeight = 100,
                impactSynth = "synth.nex_smoke_impact",
                maxHit = 33,
                penetration = 50,
                minFlight = 60,
            )
        private const val POISON_DAMAGE = 8
        private const val POISON_CHANCE = 3
        private const val POISON_OUT_OF = 47

        private const val MELEE_START_CHANCE = 18
        private const val MELEE_START_OUT_OF = 31

        private const val PROTECT_MAGIC = "varbit.prayer_protectfrommagic"
        private const val DRAG_RANGE = 6
        private const val DRAG_OUT_OF = 4
        private const val DRAG_PRAYING_OUT_OF = 8
        private const val DRAG_MESSAGE = "<col=ff3045>Nex draws you in...</col>"

        private const val VIRUS_LINE = "Let the virus flow through you!"
        private const val VIRUS_SEQ = "seq.nex_alternate_cast_attack"
        private const val VIRUS_SYNTH = "synth.nex_virus_cast"
        private const val VIRUS_MESSAGE_DELAY = 1
        private const val VIRUS_END_DELAY = 11
        private val COUGH_DELAYS = intArrayOf(2, 5, 8)
        private const val VIRUS_START_MESSAGE =
            "<col=a53fff>You become surrounded by smoke clouds!</col>"
        private const val VIRUS_END_MESSAGE =
            "<col=229628>The smoke clouds around you dissipate.</col>"
        private const val COUGH = "*Cough*"
        private const val COUGH_SPOTANIM = "spotanim.darkbow_smoke_arrow_impact"
        private const val COUGH_HEIGHT = 160
        private const val COUGH_SYNTH = "synth.nex_cough"
        private const val COUGH_DRAIN = 4

        private const val DASH_LINE = "There is..."
        private const val DASH_LEAP_LINE = "NO ESCAPE!"
        private const val DASH_SEQ = "seq.nex_dash_attack"
        private const val DASH_SEQ_DELAY = 80
        private const val DASH_LEAP_DELAY = 5
        private const val DASH_RETURN_DELAY = 6
        private const val DASH_RESET_DELAY = 9
        private const val DASH_BUSY_TICKS = 10
        private const val DASH_NEXT_ATTACK = 12
        private const val DASH_RETURN_CYCLES = 60
        private const val DASH_LENGTH = 10
        private val DASH_AISLES =
            listOf(Direction.North, Direction.East, Direction.South, Direction.West)
    }
}
