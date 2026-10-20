package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
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
import org.rsmod.api.player.vars.intVarBit
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
) {
    private var Player.nexBarrier by intVarBit("varbit.nex_barrier")

    fun schedule(session: InstanceSession, delay: Int) {
        val fight = fights.getOrCreate(session.id)
        if (fight.spawnScheduled || fight.inProgress) return
        fight.spawnScheduled = true
        deps.worldQueues.add(delay) { spawn(session) }
    }

    fun playersIn(session: InstanceSession): List<Player> =
        deps.playerList.filter { manager.sessionForPlayer(it) == session }

    private fun spawn(session: InstanceSession) {
        val fight = fights.of(session.id) ?: return
        fight.spawnScheduled = false
        if (manager.sessionForId(session.id) !== session) return
        val players = playersIn(session)
        if (players.isEmpty()) return
        val coords = manager.resolveCoord(session, NEX_TILE) ?: return
        val nex = Npc(npcType(NEX_SPAWNING), coords)
        nex.respawnDir = Direction.West
        deps.npcRepo.add(nex, Int.MAX_VALUE)
        manager.attachNpc(session.id, nex)
        fight.nex = nex
        fight.mages.clear()
        nex.say(SPAWN_LINE)
        nex.anim(SPAWN_SEQ)
        for (player in players) {
            player.nexBarrier = BARRIER_NEX_ALIVE
            bossHpBar.onOpen(player, nex)
            music.fightStart(player)
        }
        NexMage.entries.forEachIndexed { index, mage ->
            deps.worldQueues.add(FIRST_SUMMON_DELAY + index * SUMMON_INTERVAL) {
                summon(session, fight, nex, mage)
            }
        }
        deps.worldQueues.add(SMOKE_PHASE_DELAY) { awaken(session, fight, nex) }
    }

    private fun summon(session: InstanceSession, fight: NexFight, nex: Npc, mage: NexMage) {
        if (fight.nex !== nex || !nex.isSlotAssigned) return
        val coords = manager.resolveCoord(session, mage.tile) ?: return
        nex.say(mage.summonLine)
        nex.anim(mage.summonSeq)
        val npc = Npc(npcType(mage.npc), coords)
        npc.respawnDir = mage.facing
        deps.npcRepo.add(npc, Int.MAX_VALUE)
        manager.attachNpc(session.id, npc)
        fight.mages += npc
        npc.anim(MAGE_CAST_SEQ)
        npc.spotanim(MAGE_ARRIVE_SPOTANIM)
        beam(npc, nex)
    }

    private fun awaken(session: InstanceSession, fight: NexFight, nex: Npc) {
        if (fight.nex !== nex || !nex.isSlotAssigned) return
        nex.transmog(npcType(NEX), Int.MAX_VALUE)
        nex.showAllOps()
        nex.say(SMOKE_PHASE_LINE)
        fight.mages.firstOrNull()?.let { smokeMage ->
            smokeMage.anim(MAGE_CAST_SEQ)
            beam(smokeMage, nex)
        }
        fight.attackableTick = deps.mapClock.cycle + IMMUNE_TICKS
        deps.startEncounter(nex, NexSpecs.spec)
        deps.suppressAttacks(nex, IMMUNE_TICKS)
        val players = playersIn(session)
        players.firstOrNull()?.let(nex::facePlayer)
        players.forEach { bossHpBar.onUpdate(it, nex) }
    }

    private fun beam(source: Npc, nex: Npc) {
        val type = ServerCacheManager.getProjectile(MAGE_BEAM.asRSCM(RSCMType.PROJANIM)) ?: return
        val spotanim = MAGE_BEAM_SPOTANIM.asRSCM(RSCMType.SPOTANIM)
        deps.worldRepo.projAnim(ProjAnim.fromNpcToNpc(source, nex, spotanim, type))
    }

    private fun npcType(symbol: String) =
        ServerCacheManager.getNpc(symbol.asRSCM(RSCMType.NPC)) ?: error("Missing npc type: $symbol")

    private enum class NexMage(
        val npc: String,
        val summonLine: String,
        val summonSeq: String,
        val tile: CoordGrid,
        val facing: Direction,
    ) {
        Fumus(
            "npc.nex_smokemage",
            "Fumus!",
            CAST_SEQ,
            CoordGrid(2913, 5215, 0),
            Direction.SouthEast,
        ),
        Umbra(
            "npc.nex_shadowmage",
            "Umbra!",
            ALT_CAST_SEQ,
            CoordGrid(2937, 5215, 0),
            Direction.SouthWest,
        ),
        Cruor(
            "npc.nex_bloodmage",
            "Cruor!",
            CAST_SEQ,
            CoordGrid(2937, 5191, 0),
            Direction.NorthWest,
        ),
        Glacies(
            "npc.nex_icemage",
            "Glacies!",
            ALT_CAST_SEQ,
            CoordGrid(2913, 5191, 0),
            Direction.NorthEast,
        ),
    }

    internal companion object {
        const val BARRIER_IN_INSTANCE = 2
        const val BARRIER_NEX_ALIVE = 3

        private const val NEX_SPAWNING = "npc.nex_spawning"
        private const val NEX = "npc.nex"
        private val NEX_TILE = CoordGrid(2924, 5202, 0)

        private const val SPAWN_LINE = "AT LAST!"
        private const val SMOKE_PHASE_LINE = "Fill my soul with smoke!"
        private const val SPAWN_SEQ = "seq.nex_blast_away"
        private const val CAST_SEQ = "seq.nex_cast_attack"
        private const val ALT_CAST_SEQ = "seq.nex_alternate_cast_attack"
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
