package org.rsmod.content.bosses.nex

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.bosses.runtime.suppressAttacks
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.hit.NpcHitpointsFloorHook
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statSub
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit

@Singleton
internal class NexPhases
@Inject
constructor(
    private val deps: BossDeps,
    private val fights: NexFights,
    private val spawn: NexSpawn,
    private val casts: NexCasts,
    private val shadow: NexShadow,
    private val music: NexMusic,
    private val interactions: AiPlayerInteractions,
) {
    fun combatTick(nex: Npc) {
        val fight = fights.fightFor(nex) ?: return
        if (fight.nex !== nex || fight.lineSaid) return
        val row = fight.phaseRow ?: return
        if (!NexThresholds.crossed(nex.hitpoints, nex.baseHitpointsLvl, row.threshold)) return
        if (deps.mapClock.cycle < deps.encounter(nex).busyUntil) return
        fight.lineSaid = true
        fights.shout(fight, nex, row.failLine)
        val mage = fight.mageOf(fight.phase) ?: return
        deps.worldQueues.add(MAGE_ENGAGE_DELAY) { engageMage(fight, mage) }
    }

    fun floor(npc: Npc): Int {
        val fight = fights.fightFor(npc) ?: return 0
        if (fight.nex !== npc || fight.mageDown) return 0
        val row = fight.phaseRow ?: return 0
        return NexThresholds.floor(npc.baseHitpointsLvl, row.threshold)
    }

    fun onNexHit(event: NpcHitEvents.Impact) {
        val hit = event.hit
        if (!hit.isFromPlayer || hit.damage <= 0) return
        val floor = floor(event.npc)
        if (floor <= 0 || event.npc.hitpoints != floor) return
        hit.resolvePlayerSource(deps.playerList)?.mes(NEX_IMMUNE)
    }

    private fun engageMage(fight: NexFight, mage: Npc) {
        if (!mage.isSlotAssigned || mage.hitpoints <= 0) return
        if (NexSpecs.MAGES.keys.none { it.asRSCM(RSCMType.NPC) == mage.type.id }) return
        val target =
            fights
                .players(fight)
                .filter { it.coords.chebyshevDistance(mage.coords) <= MAGE_AGGRESSION_RANGE }
                .minByOrNull { it.coords.chebyshevDistance(mage.coords) } ?: return
        mage.apPlayer2(target, interactions)
    }

    fun mageAttack(mage: Npc, target: Player) {
        val fight = fights.fightFor(mage) ?: return
        if (!fight.mageReleased(mage)) return
        val attack = MAGE_ATTACKS.entries.firstOrNull { (symbol, _) ->
            symbol.asRSCM(RSCMType.NPC) == mage.type.id
        }?.value ?: return
        mage.anim(MAGE_CAST_SEQ)
        deps.worldRepo.soundArea(mage.coords, attack.castSynth, radius = MAGE_CAST_SOUND_RADIUS)
        val result = casts.cast(mage, target, attack.spell) ?: return
        if (attack.blinds && result.damage != null) {
            deps.worldQueues.add(result.impactTick) { blind(target) }
        }
    }

    private fun blind(target: Player) {
        if (!target.isValidTarget()) return
        val attack = target.stat(ATTACK)
        if (attack < target.statBase(ATTACK)) return
        target.statSub(ATTACK, NexDrains.blindDrain(attack), 0)
        target.mes(BLINDED)
    }

    suspend fun StandardNpcAccess.mageDeath() {
        val fight = fights.fightFor(npc)
        noneMode()
        hideAllOps()
        anim(MAGE_DEATH_SEQ)
        if (fight != null && fight.mageOf(fight.phase) === npc) {
            shadow.recede(fight)
            fight.mageDown = true
            deps.worldQueues.add(NEXT_PHASE_DELAY) { nextPhase(fight) }
        }
        delay(MAGE_DESPAWN_DELAY)
        deps.npcRepo.del(npc, Int.MAX_VALUE)
    }

    private fun nextPhase(fight: NexFight) {
        val nex = fight.nex ?: return
        if (!nex.isSlotAssigned || nex.hitpoints <= 0) return
        fight.phase++
        fight.lineSaid = false
        fight.mageDown = false
        val row = fight.phaseRow ?: return
        fights.shout(fight, nex, row.phaseLine)
        fight.mageOf(fight.phase)?.let { spawn.castBeam(it, nex) }
        if (fight.phase == LATER_MUSIC_PHASE) fights.players(fight).forEach(music::playLaterPhases)
        val phase = NexSpecs.PHASES.getOrNull(fight.phase) ?: return
        deps.encounter(nex).transitionTo(phase, deps.mapClock.cycle)
        deps.suppressAttacks(nex, PHASE_OPENING_TICKS)
    }

    private companion object {
        private const val NEX_IMMUNE = "Nex is immune to your attacks!"
        private const val MAGE_ENGAGE_DELAY = 1
        private const val MAGE_AGGRESSION_RANGE = 10
        private const val MAGE_CAST_SEQ = "seq.zaros_vertical_casting"
        private const val MAGE_CAST_SOUND_RADIUS = 12
        private const val MAGE_DEATH_SEQ = "seq.human_death"
        private const val NEXT_PHASE_DELAY = 1
        private const val MAGE_DESPAWN_DELAY = 2
        private const val PHASE_OPENING_TICKS = 4
        private const val LATER_MUSIC_PHASE = 2
        private const val ATTACK = "stat.attack"
        private const val BLINDED = "You have been blinded!"
        private val MAGE_ATTACKS =
            mapOf(
                "npc.nex_smokemage" to
                    NexMageAttack(
                        castSynth = "synth.smoke_cast",
                        spell =
                            NexSpell(
                                spotanim = "spotanim.smoke_barrage_travel",
                                travel = "projanim.nex_mage_smoke_barrage",
                                impact = "spotanim.smoke_barrage_impact",
                                impactHeight = 124,
                                impactSynth = "synth.smoke_barrage_impact",
                                maxHit = 29,
                                penetration = 0,
                            ),
                    ),
                "npc.nex_shadowmage" to
                    NexMageAttack(
                        castSynth = "synth.shadow_cast",
                        spell =
                            NexSpell(
                                spotanim = null,
                                travel = null,
                                impact = "spotanim.shadow_barrage_impact",
                                impactHeight = 0,
                                impactSynth = "synth.shadow_barrage_impact",
                                maxHit = 29,
                                penetration = 0,
                                minEndTime = 60,
                            ),
                        blinds = true,
                    ),
            )
    }
}

internal class NexMageAttack(
    val castSynth: String,
    val spell: NexSpell,
    val blinds: Boolean = false,
)

internal class NexFloorHook @Inject constructor(private val phases: NexPhases) :
    NpcHitpointsFloorHook {
    override fun floor(npc: Npc, hit: Hit): Int = phases.floor(npc)
}
