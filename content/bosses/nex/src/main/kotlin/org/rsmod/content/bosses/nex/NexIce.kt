package org.rsmod.content.bosses.nex

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.CombatStance
import org.rsmod.api.combat.commons.fx.MeleeAnimationAndSound
import org.rsmod.api.combat.commons.types.AttackType
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.disableOverheadPrayers
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.type.getOrNull
import org.rsmod.map.CoordGrid

@Singleton
internal class NexIce
@Inject
constructor(
    private val deps: BossDeps,
    private val fights: NexFights,
    private val casts: NexCasts,
    private val melee: NexMelee,
    private val locRepo: LocRepository,
    private val attackTypes: AttackTypes,
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
        when (fight.specials.next(NexSpecialCounter.ICE)) {
            NexSpecial.Prison -> prison(fight, nex, target)
            NexSpecial.Contain -> contain(fight, nex, target)
            else -> Unit
        }
    }

    fun breakIcicle(player: Player, loc: BoundLocInfo) {
        val fight = fights.fightOf(player) ?: return
        val prison = fight.prisons.firstOrNull { loc.coords in it.icicles } ?: return
        if (fight.prisons.any { !it.broken && player in it.prisoners }) {
            player.mes(FROZEN_MESSAGE)
            return
        }
        if (attackTypes.get(player) !in BREAKING_TYPES) {
            player.mes(UNSUITABLE_MESSAGE)
            return
        }
        player.anim(attackAnim(player))
        if (deps.random.of(BREAK_OUT_OF) >= BREAK_CHANCE) {
            player.mes(BREAK_FAILED_MESSAGE)
            return
        }
        locRepo.del(loc, Int.MAX_VALUE)
        player.mes(BREAK_MESSAGE, ChatType.Spam)
        if (prison.broken) return
        prison.broken = true
        for (prisoner in prison.prisoners) {
            if (!prisoner.isValidTarget()) continue
            prisoner.mes(FREED_MESSAGE)
            CombatEffects.unfreeze(prisoner)
        }
    }

    private fun cast(fight: NexFight, nex: Npc) {
        nex.anim(CAST_SEQ)
        val centre = NexGeometry.centre(nex.coords, nex.size)
        deps.worldRepo.soundArea(centre, CAST_SYNTH, radius = SOUND_RADIUS)
        val targets =
            fights.players(fight).filter {
                NexGeometry.footprintDistance(nex.coords, nex.size, it.coords) <= ATTACK_RANGE
            }
        for (target in targets) {
            val result = casts.cast(nex, target, ICE_SPELL) ?: continue
            val damage = result.damage ?: continue
            val drain = NexDrains.castPrayerDrain(damage)
            if (drain > 0) deps.worldQueues.add(result.impactTick) { drainPrayer(target, drain) }
        }
    }

    private fun drainPrayer(player: Player, amount: Int) {
        if (player.isValidTarget()) player.statSub(PRAYER, amount, 0)
    }

    private fun prison(fight: NexFight, nex: Npc, target: Player) {
        fights.shout(fight, nex, PRISON_LINE)
        nex.anim(CAST_SEQ)
        val centre = NexGeometry.centre(nex.coords, nex.size)
        deps.worldRepo.soundArea(centre, PRISON_CAST_SYNTH, radius = SOUND_RADIUS)
        deps.encounter(nex).nextAttackTick = deps.mapClock.cycle + PRISON_NEXT_ATTACK
        casts.launch(nex, target, PRISON_SPOTANIM, PRISON_TRAVEL, MIN_END_TIME) ?: return
        val distance = NexGeometry.footprintDistance(nex.coords, nex.size, target.coords)
        deps.worldQueues.add(NexGeometry.magicImpactTick(distance)) { trap(fight, nex, target) }
    }

    private fun trap(fight: NexFight, nex: Npc, target: Player) {
        if (fight.nex !== nex || nex.hitpoints <= 0 || !target.isValidTarget()) return
        val centre = target.coords
        val prisoners = fights.players(fight).filter { it.coords == centre }
        val icicles = NexGeometry.ring(centre, 1).filter { !deps.collision.isWalkBlocked(it) }
        addLoc(centre, BLOCKING_LOC, PRISON_TICKS)
        icicles.forEach { addLoc(it, PRISON_ICICLE, PRISON_TICKS) }
        deps.worldRepo.soundArea(centre, ICE_TRAP_SYNTH, radius = PRISON_SOUND_RADIUS)
        val prison = NexPrison(icicles, prisoners)
        fight.prisons += prison
        for (prisoner in prisoners) {
            prisoner.mes(TRAPPED_MESSAGE)
            injure(prisoner)
            bind(prisoner, PRISON_TICKS)
        }
        deps.worldQueues.add(PRISON_TICKS) { shatter(fight, nex, prison) }
    }

    private fun shatter(fight: NexFight, nex: Npc, prison: NexPrison) {
        fight.prisons -= prison
        if (prison.broken) return
        for (prisoner in prison.prisoners) {
            if (!prisoner.isValidTarget()) continue
            prisoner.mes(SHATTER_MESSAGE)
            prisoner.soundSynth(SHATTER_SYNTH)
            if (fight.nex !== nex || nex.hitpoints <= 0) continue
            val damage = deps.random.of(SHATTER_MIN_HIT, SHATTER_MAX_HIT)
            prisoner.queueHit(
                nex,
                SHATTER_HIT_DELAY,
                HitType.Ranged,
                damage,
                deps.playerHitModifier,
                penetration = MISSILES_PENETRATION,
            )
        }
    }

    private fun contain(fight: NexFight, nex: Npc, target: Player) {
        fights.shout(fight, nex, CONTAIN_LINE)
        nex.anim(CONTAIN_SEQ)
        nex.noneMode()
        nex.resetFaceEntity()
        nex.movementLocked = true
        deps.encounter(nex).nextAttackTick = deps.mapClock.cycle + CONTAIN_NEXT_ATTACK
        deps.worldQueues.add(CONTAIN_DELAY) { spike(fight, nex, target) }
    }

    private fun spike(fight: NexFight, nex: Npc, target: Player) {
        if (fight.nex !== nex || nex.hitpoints <= 0) return
        nex.movementLocked = false
        if (target.isValidTarget()) {
            nex.facePlayer(target)
            nex.apPlayer2(target, interactions)
        }
        val footprint = NexGeometry.footprint(nex.coords, nex.size)
        val ring = NexGeometry.ring(nex.coords, nex.size)
        val players = fights.players(fight)
        val spiked = players.filter { it.coords in ring || it.coords in footprint }
        val occupied = spiked.map { it.coords }.toSet()
        footprint.forEach { addLoc(it, BLOCKING_LOC, CONTAIN_TICKS) }
        for (tile in ring) {
            if (tile in occupied || !deps.collision.isWalkBlocked(tile)) {
                addLoc(tile, CONTAIN_ICICLE, CONTAIN_TICKS)
            }
        }
        val centre = NexGeometry.centre(nex.coords, nex.size)
        deps.worldRepo.soundArea(centre, ICE_TRAP_SYNTH, radius = CONTAIN_SOUND_RADIUS)
        for (player in spiked) {
            player.mes(SPIKE_MESSAGE)
            player.soundSynth(SPIKE_SYNTH, delay = SPIKE_SYNTH_DELAY)
            injure(player)
            bind(player, CONTAIN_TICKS)
            val damage = deps.random.of(CONTAIN_MIN_HIT, CONTAIN_MAX_HIT)
            player.queueHit(
                nex,
                CONTAIN_HIT_DELAY,
                HitType.Ranged,
                damage,
                deps.playerHitModifier,
                penetration = MISSILES_PENETRATION,
            )
        }
    }

    private fun addLoc(tile: CoordGrid, loc: String, duration: Int) {
        val angle = deps.random.pick(LocAngle.entries)
        locRepo.add(tile, loc, duration, angle, LocShape.CentrepieceStraight)
    }

    private fun injure(player: Player) {
        player.disableOverheadPrayers()
        player.mes(INJURED_MESSAGE)
    }

    private fun bind(player: Player, ticks: Int) {
        player.frozen = true
        player.routeDestination.clear()
        player.timer(FREEZE_TIMER, ticks)
    }

    private fun attackAnim(player: Player): String {
        val fx = MeleeAnimationAndSound.from(CombatStance[player.vars[COMBAT_STANCE]])
        val weaponAnim = getOrNull(player.righthand)?.paramOrNull(fx.animParam)
        return weaponAnim?.let { RSCM.getReverseMapping(RSCMType.SEQ, it.id) } ?: fx.defaultAnim
    }

    private companion object {
        private const val PRAYER = "stat.prayer"
        private const val SOUND_RADIUS = 15
        private const val ATTACK_RANGE = 10
        private const val MIN_END_TIME = 60
        private const val MISSILES_PENETRATION = 50
        private const val FREEZE_TIMER = "timer.combat_freeze"
        private const val COMBAT_STANCE = "varp.com_mode"
        private const val INJURED_MESSAGE = "You've been injured and can't use protection prayers!"
        private const val BLOCKING_LOC = "loc.invisible_type8_blocking"
        private const val ICE_TRAP_SYNTH = "synth.nex_ice_trap"

        private const val MELEE_START_CHANCE = 65
        private const val MELEE_START_OUT_OF = 95

        private const val CAST_SEQ = "seq.nex_alternate_cast_attack"
        private const val CAST_SYNTH = "synth.ice_cast"
        private val ICE_SPELL =
            NexSpell(
                spotanim = "spotanim.nex_ice_attack_proj",
                travel = "projanim.nex_ice_attack",
                impact = "spotanim.nex_ice_attack_impact",
                impactHeight = 0,
                impactSynth = "synth.ice_barrage_impact",
                maxHit = 33,
                penetration = 50,
                minEndTime = MIN_END_TIME,
            )

        private const val PRISON_LINE = "Die now, in a prison of ice!"
        private const val PRISON_CAST_SYNTH = "synth.nex_ice_prison_cast"
        private const val PRISON_SPOTANIM = "spotanim.nex_ice_prison_proj"
        private const val PRISON_TRAVEL = "projanim.nex_ice_prison"
        private const val PRISON_ICICLE = "loc.nex_icicle_2"
        private const val PRISON_SOUND_RADIUS = 8
        private const val PRISON_TICKS = 8
        private const val PRISON_NEXT_ATTACK = 8
        private const val SHATTER_SYNTH = "synth.nex_ice_prison_shatter"
        private const val SHATTER_HIT_DELAY = 2
        private const val SHATTER_MIN_HIT = 44
        private const val SHATTER_MAX_HIT = 64
        private const val TRAPPED_MESSAGE =
            "<col=ff289d>You've been trapped in an ice prison!</col>"
        private const val SHATTER_MESSAGE =
            "<col=ff289d>The ice prison shatters and pierces your flesh!</col>"
        private const val FROZEN_MESSAGE =
            "<col=ff289d>You're frozen and unable to free yourself!</col>"
        private const val UNSUITABLE_MESSAGE = "Your weapon is unsuitable to break the ice."
        private const val BREAK_MESSAGE = "<col=ff289d>You manage to destroy the icicle!</col>"
        private const val BREAK_FAILED_MESSAGE = "<col=ff289d>You fail to destroy the icicle!</col>"
        private const val FREED_MESSAGE = "<col=229628>You've been freed from the ice prison!</col>"
        private const val BREAK_CHANCE = 3
        private const val BREAK_OUT_OF = 5
        private val BREAKING_TYPES = setOf(AttackType.Stab, AttackType.Crush)

        private const val CONTAIN_LINE = "Contain this!"
        private const val CONTAIN_SEQ = "seq.nex_smash_attack"
        private const val CONTAIN_ICICLE = "loc.nex_icicle_1"
        private const val CONTAIN_DELAY = 5
        private const val CONTAIN_TICKS = 10
        private const val CONTAIN_NEXT_ATTACK = 7
        private const val CONTAIN_SOUND_RADIUS = 12
        private const val CONTAIN_HIT_DELAY = 2
        private const val CONTAIN_MIN_HIT = 33
        private const val CONTAIN_MAX_HIT = 60
        private const val SPIKE_SYNTH = "synth.nex_contain_spike"
        private const val SPIKE_SYNTH_DELAY = 20
        private const val SPIKE_MESSAGE = "<col=a53fff>Nex's icicles spike you to the spot!</col>"
    }
}

internal class NexPrison(val icicles: List<CoordGrid>, val prisoners: List<Player>) {
    var broken: Boolean = false
}
