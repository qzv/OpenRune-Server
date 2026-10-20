package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.hit.NpcHitHealHook
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statSub
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.EntityTinting
import org.rsmod.game.hit.Hit
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.map.CoordGrid

@Singleton
internal class NexBlood
@Inject
constructor(
    private val deps: BossDeps,
    private val fights: NexFights,
    private val casts: NexCasts,
    private val melee: NexMelee,
    private val interactions: AiPlayerInteractions,
) {
    fun standard(nex: Npc, target: Player) {
        val fight = fights.fightFor(nex) ?: return
        if (!melee.attempt(fight, nex, target, MELEE_START_CHANCE, MELEE_START_OUT_OF)) {
            cast(fight, nex, target)
        }
    }

    fun special(nex: Npc, target: Player) {
        val fight = fights.fightFor(nex) ?: return
        when (fight.specials.next(NexSpecialCounter.BLOOD)) {
            NexSpecial.Siphon -> siphon(fight, nex)
            NexSpecial.Sacrifice -> sacrifice(fight, nex, target)
            else -> Unit
        }
    }

    fun siphoning(npc: Npc): Boolean {
        val fight = fights.fightFor(npc) ?: return false
        if (fight.nex !== npc) return false
        return NexBloodRules.siphoning(deps.mapClock.cycle, fight.siphonFrom, fight.siphonUntil)
    }

    fun endPhase(fight: NexFight) {
        fight.siphonUntil = Int.MIN_VALUE
        fight.reaver?.takeIf { it.isAlive }?.let(::kill)
        fight.reaver = null
    }

    fun reaverAttack(reaver: Npc, target: Player) {
        reaver.anim(REAVER_ATTACK_SEQ)
        target.soundSynth(REAVER_CAST_SYNTH, delay = REAVER_CAST_SYNTH_DELAY)
        casts.cast(reaver, target, REAVER_SPELL)
    }

    fun kill(reaver: Npc) {
        if (!reaver.isSlotAssigned) return
        reaver.hitpoints = 0
        reaver.noneMode()
        reaver.hideAllOps()
        reaver.anim(REAVER_DEATH_SEQ)
        deps.worldQueues.add(REAVER_DESPAWN_DELAY) {
            if (reaver.isSlotAssigned) deps.npcRepo.del(reaver, Int.MAX_VALUE)
        }
    }

    private fun cast(fight: NexFight, nex: Npc, target: Player) {
        nex.anim(CAST_SEQ)
        val centre = NexGeometry.centre(nex.coords, nex.size)
        deps.worldRepo.soundArea(centre, CAST_SYNTH, radius = SOUND_RADIUS)
        val victims =
            fights.players(fight).filter {
                it === target || it.coords.chebyshevDistance(target.coords) <= SPLASH_RADIUS
            }
        val dealt = HashMap<Int, Int>()
        for (victim in victims) {
            val result = casts.cast(nex, victim, BLOOD_SPELL) ?: continue
            val damage = result.damage ?: continue
            if (damage <= 0) continue
            dealt.merge(result.impactTick, damage, Int::plus)
            deps.worldQueues.add(result.impactTick) {
                drainPrayer(victim, NexDrains.castPrayerDrain(damage))
            }
        }
        for ((tick, damage) in dealt) {
            val heal = NexBloodRules.standardHeal(damage)
            deps.worldQueues.add(tick) { fights.heal(fight, nex, heal) }
        }
    }

    private fun siphon(fight: NexFight, nex: Npc) {
        val cycle = deps.mapClock.cycle
        fights.shout(fight, nex, SIPHON_LINE)
        nex.anim(SIPHON_SEQ)
        nex.spotanim(SIPHON_SPOTANIM)
        nex.movementLocked = true
        fight.siphonFrom = cycle + SIPHON_HEAL_FROM
        fight.siphonUntil = cycle + SIPHON_HEAL_UNTIL
        deps.encounter(nex).nextAttackTick = cycle + SIPHON_NEXT_ATTACK
        fight.reaver?.takeIf { it.isAlive }?.let { replaceReaver(fight, nex, it) }
        summonReaver(fight, nex)
        deps.worldQueues.add(SIPHON_KNEEL_TICKS) {
            if (fight.nex !== nex || nex.hitpoints <= 0) return@add
            nex.movementLocked = false
            nex.resetAnim()
        }
    }

    private fun replaceReaver(fight: NexFight, nex: Npc, old: Npc) {
        deps.worldQueues.add(OLD_REAVER_DEATH_DELAY) {
            if (!old.isAlive) return@add
            val remaining = old.hitpoints
            kill(old)
            deps.worldQueues.add(OLD_REAVER_HEAL_DELAY) { fights.heal(fight, nex, remaining) }
        }
    }

    private fun summonReaver(fight: NexFight, nex: Npc) {
        val type = ServerCacheManager.getNpc(REAVER.asRSCM(RSCMType.NPC)) ?: return
        val tile = reaverTile(nex, type.size) ?: return
        val reaver = Npc(type, tile)
        deps.npcRepo.add(reaver, Int.MAX_VALUE)
        fights.attach(fight, reaver)
        fight.reaver = reaver
        reaver.spotanim(SUMMON_SPOTANIM)
        deps.worldRepo.soundArea(tile, SIPHON_SYNTH, radius = SIPHON_SOUND_RADIUS)
        deps.worldQueues.add(REAVER_ENGAGE_DELAY) { engage(fight, reaver) }
    }

    private fun reaverTile(nex: Npc, size: Int): CoordGrid? {
        val min = -(REAVER_RANGE + size - 1)
        val max = nex.size + REAVER_RANGE - 1
        repeat(REAVER_TILE_ATTEMPTS) {
            val tile = nex.coords.translate(deps.random.of(min, max), deps.random.of(min, max))
            if (walkable(tile, size)) return tile
        }
        return null
    }

    private fun walkable(sw: CoordGrid, size: Int): Boolean {
        for (x in 0 until size) {
            for (z in 0 until size) {
                if (deps.collision.isWalkBlocked(sw.translate(x, z))) return false
            }
        }
        return true
    }

    private fun engage(fight: NexFight, reaver: Npc) {
        if (!reaver.isAlive) return
        val target =
            fights.players(fight).minByOrNull { player ->
                NexGeometry.footprintDistance(reaver.coords, reaver.size, player.coords)
            } ?: return
        reaver.apPlayer2(target, interactions)
    }

    private fun sacrifice(fight: NexFight, nex: Npc, target: Player) {
        val marked = deps.random.pickOrNull(fights.players(fight)) ?: return
        fights.shout(fight, nex, SACRIFICE_LINE)
        nex.noneMode()
        nex.resetFaceEntity()
        val centre = NexGeometry.centre(nex.coords, nex.size)
        deps.worldRepo.soundArea(centre, SACRIFICE_CALL_SYNTH, radius = SOUND_RADIUS)
        marked.mes(MARK_MESSAGE)
        marked.soundSynth(MARK_SYNTH)
        marked.tint(MARK_TINT)
        deps.encounter(nex).nextAttackTick = deps.mapClock.cycle + SACRIFICE_ESCAPED_NEXT_ATTACK
        deps.worldQueues.add(SACRIFICE_RESOLVE_DELAY) {
            resolve(fight, nex, marked)
            if (fight.nex === nex && nex.hitpoints > 0 && target.isValidTarget()) {
                nex.apPlayer2(target, interactions)
            }
        }
    }

    private fun resolve(fight: NexFight, nex: Npc, marked: Player) {
        if (fight.nex !== nex || nex.hitpoints <= 0 || !marked.isValidTarget()) return
        if (NexBloodRules.escapedSacrifice(distance(nex, marked))) {
            marked.mes(ESCAPED_MESSAGE)
            return
        }
        marked.mes(FAILED_MESSAGE)
        deps.encounter(nex).nextAttackTick = deps.mapClock.cycle + SACRIFICE_FAILED_NEXT_ATTACK
        nex.facePlayer(marked)
        nex.anim(CAST_SEQ)
        val centre = NexGeometry.centre(nex.coords, nex.size)
        deps.worldRepo.soundArea(centre, SACRIFICE_CAST_SYNTH, radius = SOUND_RADIUS)
        val dealt = HashMap<Int, Int>()
        sacrificeHit(nex, marked, SACRIFICE_SPELL, dealt)
        for (player in fights.players(fight)) {
            if (player === marked || NexBloodRules.escapedSacrifice(distance(nex, player))) continue
            player.mes(BYSTANDER_MESSAGE)
            sacrificeHit(nex, player, BYSTANDER_SPELL, dealt)
        }
        for ((tick, damage) in dealt) {
            val heal = NexBloodRules.sacrificeHeal(damage)
            deps.worldQueues.add(tick) { fights.heal(fight, nex, heal) }
        }
    }

    private fun sacrificeHit(
        nex: Npc,
        victim: Player,
        spell: NexSpell,
        dealt: MutableMap<Int, Int>,
    ) {
        val result = casts.cast(nex, victim, spell) ?: return
        deps.worldQueues.add(result.impactTick) {
            drainPrayer(victim, NexBloodRules.sacrificePrayerDrain(victim.stat(PRAYER)))
        }
        val damage = result.damage ?: return
        if (damage > 0) dealt.merge(result.impactTick, damage, Int::plus)
    }

    private fun distance(nex: Npc, player: Player): Int =
        NexGeometry.footprintDistance(nex.coords, nex.size, player.coords)

    private fun drainPrayer(player: Player, amount: Int) {
        if (amount > 0 && player.isValidTarget()) player.statSub(PRAYER, amount, 0)
    }

    private val Npc.isAlive: Boolean
        get() = isSlotAssigned && hitpoints > 0

    private companion object {
        private const val SOUND_RADIUS = 15

        private const val MELEE_START_CHANCE = 27
        private const val MELEE_START_OUT_OF = 46

        private const val CAST_SEQ = "seq.nex_alternate_cast_attack"
        private const val CAST_SYNTH = "synth.nex_blood_cast"
        private const val SPLASH_RADIUS = 1
        private val BLOOD_SPELL =
            NexSpell(
                spotanim = "spotanim.nex_blood_attack_proj",
                travel = "projanim.nex_blood_attack",
                impact = "spotanim.nex_blood_attack_impact",
                impactHeight = 0,
                impactSynth = "synth.nex_blood_impact",
                maxHit = 33,
                penetration = 50,
                minFlight = 60,
                alwaysHits = true,
            )

        private const val SIPHON_LINE = "A siphon will solve this!"
        private const val SIPHON_SEQ = "seq.nex_blood_siphon"
        private const val SIPHON_SPOTANIM = "spotanim.nex_blood_siphon"
        private const val SIPHON_SYNTH = "synth.nex_blood_siphon"
        private const val SIPHON_SOUND_RADIUS = 8
        private const val SIPHON_HEAL_FROM = 2
        private const val SIPHON_HEAL_UNTIL = 8
        private const val SIPHON_KNEEL_TICKS = 8
        private const val SIPHON_NEXT_ATTACK = 10
        private const val OLD_REAVER_DEATH_DELAY = 2
        private const val OLD_REAVER_HEAL_DELAY = 1

        private const val REAVER = "npc.nex_prison_blood_reaver_boss"
        private const val REAVER_RANGE = 8
        private const val REAVER_TILE_ATTEMPTS = 20
        private const val REAVER_ENGAGE_DELAY = 1
        private const val REAVER_DESPAWN_DELAY = 3
        private const val SUMMON_SPOTANIM = "spotanim.nex_summon"
        private const val REAVER_ATTACK_SEQ = "seq.blood_reaver_attack"
        private const val REAVER_DEATH_SEQ = "seq.blood_reaver_death"
        private const val REAVER_CAST_SYNTH = "synth.blood_cast"
        private const val REAVER_CAST_SYNTH_DELAY = 30
        private val REAVER_SPELL =
            NexSpell(
                spotanim = "spotanim.blood_rush_travel",
                travel = "projanim.nex_reaver_blood_rush",
                impact = "spotanim.blood_rush_impact",
                impactHeight = 0,
                impactSynth = "synth.blood_rush_impact",
                maxHit = 20,
                penetration = 0,
                minFlight = 60,
            )

        private const val SACRIFICE_LINE = "I demand a blood sacrifice!"
        private const val SACRIFICE_CALL_SYNTH = "synth.nex_sacrifice_call"
        private const val SACRIFICE_CAST_SYNTH = "synth.nex_sacrifice_cast"
        private const val MARK_SYNTH = "synth.nex_sacrifice_mark"
        private val MARK_TINT =
            EntityTinting(
                startCycle = 0,
                endCycle = 210,
                hue = 0,
                saturation = 6,
                lightness = 28,
                weight = 112,
            )
        private const val MARK_MESSAGE =
            "<col=ff3045>Nex has marked you for a blood sacrifice! RUN!</col>"
        private const val ESCAPED_MESSAGE =
            "<col=229628>You managed to escape from Nex's blood sacrifice.</col>"
        private const val FAILED_MESSAGE =
            "<col=ff3045>You failed to escape from Nex's blood sacrifice!</col>"
        private const val BYSTANDER_MESSAGE = "<col=ff3045>Nex unleashes a blood sacrifice!</col>"
        private const val SACRIFICE_RESOLVE_DELAY = 6
        private const val SACRIFICE_ESCAPED_NEXT_ATTACK = 7
        private const val SACRIFICE_FAILED_NEXT_ATTACK = 4
        private val SACRIFICE_SPELL =
            NexSpell(
                spotanim = "spotanim.nex_blood_siphon_proj",
                travel = "projanim.nex_blood_attack",
                impact = "spotanim.spell_blood_siphon_impact",
                impactHeight = 0,
                impactSynth = "synth.nex_sacrifice_impact",
                maxHit = 80,
                penetration = 62,
                minFlight = 60,
                alwaysHits = true,
            )
        private val BYSTANDER_SPELL = SACRIFICE_SPELL.copy(maxHit = 12, penetration = 100)
    }
}

internal class NexSiphonHook @Inject constructor(private val blood: NexBlood) : NpcHitHealHook {
    override fun heals(npc: Npc, hit: Hit): Boolean = hit.isFromPlayer && blood.siphoning(npc)
}
