package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

@Singleton
internal class NexKill
@Inject
constructor(
    private val deps: BossDeps,
    private val fights: NexFights,
    private val manager: InstanceManager,
    private val spawn: NexSpawn,
    private val loot: NexLoot,
) {
    private var Player.nexKills by intVarp("varp.total_nex_kills")
    private var Player.nexBestTime by intVarp("varp.nex_best_time")

    suspend fun StandardNpcAccess.nexDeath() {
        val fight = fights.fightFor(npc)
        val wrathTick = deps.mapClock.cycle
        noneMode()
        hideAllOps()
        npc.resetFaceEntity()
        val dying = ServerCacheManager.getNpc(DYING.asRSCM(RSCMType.NPC))
        dying?.let { npc.transmog(it, Int.MAX_VALUE) }
        if (fight != null) fights.shout(fight, npc, WRATH_LINE) else npc.say(WRATH_LINE)
        delay(DEATH_SEQ_DELAY)
        anim(DEATH_SEQ)
        delay(CLOUD_DELAY)
        cloud(NexGeometry.centre(npc.coords, npc.size))
        delay(WRATH_DELAY)
        val centre = NexGeometry.centre(npc.coords, npc.size)
        val players = fight?.let(fights::players).orEmpty()
        for (player in players) {
            if (!NexZarosRules.inWrath(centre, player.coords)) continue
            val damage = deps.random.of(WRATH_MIN_HIT, WRATH_MAX_HIT)
            player.queueHit(WRATH_HIT_DELAY, HitType.Typeless, damage, deps.playerHitModifier)
        }
        val awards = fight?.let { loot.awards(it, npc, players) }.orEmpty()
        val rolls = awards.mapValues { (player, award) -> loot.roll(player, award) }
        val event = loot.nextEvent()
        for (player in players) {
            val kills = player.nexKills + 1
            player.mes("Your Nex kill count is: <col=ff0000>$kills</col>.")
            player.nexKills = kills
            fight?.let { reportDuration(player, wrathTick - it.awakenTick) }
            val roll = rolls[player]
            roll?.let { loot.payCommons(player, it, centre, event) }
            if (roll?.mvp == true) player.mes(MVP_MESSAGE)
            player.nexBarrier = NexSpawn.BARRIER_IN_INSTANCE
            loot.nexEssence(player)
            roll?.let { loot.payRares(player, it, centre, event) }
        }
        deps.npcRepo.del(npc, Int.MAX_VALUE)
        players.forEach { it.midiSong(STOP_MUSIC, fadeOutSpeed = MUSIC_FADE_OUT) }
        if (fight != null) respawn(fight)
    }

    private fun reportDuration(player: Player, ticks: Int) {
        val time = NexLootRules.clock(ticks)
        val best = player.nexBestTime
        if (best == 0 || ticks < best) {
            player.nexBestTime = ticks
            player.mes("Fight duration: <col=ff0000>$time</col> (new personal best)")
            return
        }
        val previous = NexLootRules.clock(best)
        player.mes("Fight duration: <col=ff0000>$time</col>. Personal best: $previous")
    }

    private fun cloud(centre: CoordGrid) {
        val big = SpotanimType(CLOUD.asRSCM(RSCMType.SPOTANIM))
        val small = SpotanimType(CLOUD_SMALL.asRSCM(RSCMType.SPOTANIM))
        deps.worldRepo.spotanimMap(big, centre)
        val radius = CLOUD_SOUND_RADIUS
        deps.worldRepo.soundArea(centre, CLOUD_SYNTH, CLOUD_SYNTH_DELAY, radius = radius)
        deps.worldRepo.soundArea(centre, BLAST_SYNTH, BLAST_SYNTH_DELAY, radius = radius)
        val travelSpotanim = CLOUD_PROJECTILE.asRSCM(RSCMType.SPOTANIM)
        RING.forEachIndexed { index, (dx, dz) ->
            val tile = centre.translate(dx, dz)
            if (deps.collision.isWalkBlocked(tile)) return@forEachIndexed
            val delay = (index + 1) * RING_DELAY_STEP
            val proj = ProjAnim.fromCoordToCoord(centre, tile, travelSpotanim, CLOUD_TRAVEL)
            deps.worldRepo.projAnim(proj.copy(endTime = delay))
            deps.worldRepo.spotanimMap(small, tile, delay = delay)
        }
        for ((dx, dz) in CORNERS) {
            val tile = centre.translate(dx, dz)
            if (deps.collision.isWalkBlocked(tile)) continue
            deps.worldRepo.spotanimMap(small, tile, delay = CORNER_DELAY)
        }
    }

    private fun respawn(fight: NexFight) {
        val session = manager.sessionForId(fight.instanceId) ?: return
        val now = deps.mapClock.cycle
        val tick = NexZarosRules.respawnTick(now, fight.spawnTick)
        fights.end(fight.instanceId)
        spawn.schedule(session, tick - now)
    }

    private companion object {
        private const val DYING = "npc.nex_dying"
        private const val WRATH_LINE = "Taste my wrath!"
        private const val MVP_MESSAGE = "You were the MVP for this fight!"
        private const val DEATH_SEQ = "seq.nex_death"
        private const val DEATH_SEQ_DELAY = 1
        private const val CLOUD_DELAY = 3
        private const val WRATH_DELAY = 3
        private const val WRATH_HIT_DELAY = 1
        private const val WRATH_MIN_HIT = 26
        private const val WRATH_MAX_HIT = 43
        private const val STOP_MUSIC = "midi.stop_music"
        private const val MUSIC_FADE_OUT = 50

        private const val CLOUD = "spotanim.nex_mushroom_cloud_spotanim"
        private const val CLOUD_SMALL = "spotanim.nex_mushroom_cloud_spotanim_small"
        private const val CLOUD_PROJECTILE = "spotanim.nex_mushroom_cloud_projanim"
        private const val CLOUD_TRAVEL = "projanim.nex_mushroom_cloud"
        private const val CLOUD_SYNTH = "synth.nex_wrath_cloud"
        private const val BLAST_SYNTH = "synth.nex_wrath_blast"
        private const val CLOUD_SYNTH_DELAY = 60
        private const val BLAST_SYNTH_DELAY = 90
        private const val CLOUD_SOUND_RADIUS = 15
        private const val RING_DELAY_STEP = 4
        private const val CORNER_DELAY = 36
        private val RING =
            listOf(-3 to -3, 0 to -3, 3 to -3, -3 to 0, 3 to 0, -3 to 3, 0 to 3, 3 to 3)
        private val CORNERS = listOf(2 to -2, -2 to 2, -2 to -2, 2 to 2)
    }
}
