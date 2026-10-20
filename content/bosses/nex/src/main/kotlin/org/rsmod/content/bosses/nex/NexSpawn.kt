package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.startEncounter
import org.rsmod.api.bosses.runtime.suppressAttacks
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.table.NexPhasesRow
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.map.Direction
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

@Singleton
internal class NexSpawn
@Inject
constructor(
    private val deps: BossDeps,
    private val manager: InstanceManager,
    private val fights: NexFights,
    private val bossHpBar: BossHpBarScript,
    private val music: NexMusic,
    private val interactions: AiPlayerInteractions,
) {
    private var Player.nexBarrier by intVarBit("varbit.nex_barrier")

    fun schedule(session: InstanceSession, delay: Int) {
        val fight = fights.getOrCreate(session.id)
        if (fight.spawnScheduled || fight.inProgress) return
        fight.spawnScheduled = true
        deps.worldQueues.add(delay) { spawn(session) }
    }

    private fun spawn(session: InstanceSession) {
        val fight = fights.of(session.id) ?: return
        fight.spawnScheduled = false
        if (manager.sessionForId(session.id) !== session) return
        val players = fights.players(fight)
        if (players.isEmpty()) return
        val coords = manager.resolveCoord(session, NEX_TILE) ?: return
        val nex = Npc(npcType(NEX_SPAWNING), coords)
        nex.respawnDir = Direction.West
        deps.npcRepo.add(nex, Int.MAX_VALUE)
        manager.attachNpc(session.id, nex)
        fight.nex = nex
        fight.home = coords
        fight.spawnTick = deps.mapClock.cycle
        fight.mages.clear()
        fights.shout(fight, nex, SPAWN_LINE)
        nex.anim(SPAWN_SEQ)
        for (player in players) {
            player.nexBarrier = BARRIER_NEX_ALIVE
            bossHpBar.onOpen(player, nex)
            music.fightStart(player)
        }
        NexFight.PHASES.forEachIndexed { index, row ->
            deps.worldQueues.add(FIRST_SUMMON_DELAY + index * SUMMON_INTERVAL) {
                summon(session, fight, nex, row)
            }
        }
        deps.worldQueues.add(SMOKE_PHASE_DELAY) { awaken(fight, nex) }
    }

    private fun summon(session: InstanceSession, fight: NexFight, nex: Npc, row: NexPhasesRow) {
        if (fight.nex !== nex || !nex.isSlotAssigned) return
        val coords = manager.resolveCoord(session, row.mageTile) ?: return
        fights.shout(fight, nex, row.summonLine)
        nex.anim(RSCM.getReverseMapping(RSCMType.SEQ, row.summonSeq.id))
        val npc = Npc(row.mage, coords)
        npc.respawnDir = Direction.entries.first { it.id == row.mageFacing }
        npc.movementLocked = true
        deps.npcRepo.add(npc, Int.MAX_VALUE)
        manager.attachNpc(session.id, npc)
        fight.mages += npc
        npc.anim(MAGE_CAST_SEQ)
        npc.spotanim(MAGE_ARRIVE_SPOTANIM)
        beam(npc, nex)
    }

    private fun awaken(fight: NexFight, nex: Npc) {
        if (fight.nex !== nex || !nex.isSlotAssigned) return
        nex.transmog(npcType(NEX), Int.MAX_VALUE)
        nex.showAllOps()
        fight.phaseRow?.let { fights.shout(fight, nex, it.phaseLine) }
        fight.mageOf(fight.phase)?.let { mage -> castBeam(mage, nex) }
        fight.attackableTick = deps.mapClock.cycle + IMMUNE_TICKS
        deps.startEncounter(nex, NexSpecs.spec)
        deps.suppressAttacks(nex, IMMUNE_TICKS)
        val players = fights.players(fight)
        players.firstOrNull()?.let { target ->
            nex.facePlayer(target)
            nex.apPlayer2(target, interactions)
        }
        players.forEach { bossHpBar.onUpdate(it, nex) }
    }

    fun castBeam(mage: Npc, nex: Npc) {
        mage.anim(MAGE_CAST_SEQ)
        beam(mage, nex)
    }

    private fun beam(source: Npc, nex: Npc) {
        val type = ServerCacheManager.getProjectile(MAGE_BEAM.asRSCM(RSCMType.PROJANIM)) ?: return
        val spotanim = MAGE_BEAM_SPOTANIM.asRSCM(RSCMType.SPOTANIM)
        deps.worldRepo.projAnim(ProjAnim.fromNpcToNpc(source, nex, spotanim, type))
    }

    private fun npcType(symbol: String) =
        ServerCacheManager.getNpc(symbol.asRSCM(RSCMType.NPC)) ?: error("Missing npc type: $symbol")

    internal companion object {
        const val BARRIER_IN_INSTANCE = 2
        const val BARRIER_NEX_ALIVE = 3

        private const val NEX_SPAWNING = "npc.nex_spawning"
        private const val NEX = "npc.nex"
        private val NEX_TILE = CoordGrid(2924, 5202, 0)

        private const val SPAWN_LINE = "AT LAST!"
        private const val SPAWN_SEQ = "seq.nex_blast_away"
        private const val MAGE_CAST_SEQ = "seq.zaros_vertical_casting"
        private const val MAGE_ARRIVE_SPOTANIM = "spotanim.abyssal_demon_teleport"
        private const val MAGE_BEAM = "projanim.nex_mage_beam"
        private const val MAGE_BEAM_SPOTANIM = "spotanim.nex_spot_leech_projanim_defence"

        private const val FIRST_SUMMON_DELAY = 5
        private const val SUMMON_INTERVAL = 4
        private const val SMOKE_PHASE_DELAY = 21
        private const val IMMUNE_TICKS = 4
    }
}
