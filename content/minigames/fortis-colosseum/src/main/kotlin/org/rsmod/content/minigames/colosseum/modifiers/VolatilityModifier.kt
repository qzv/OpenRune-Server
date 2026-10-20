package org.rsmod.content.minigames.colosseum.modifiers

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.table.ColosseumEnemiesRow
import org.rsmod.content.minigames.colosseum.ColosseumModifiers
import org.rsmod.content.minigames.colosseum.ColosseumSand
import org.rsmod.content.minigames.colosseum.ColosseumWaves
import org.rsmod.content.minigames.colosseum.Modifier
import org.rsmod.content.minigames.colosseum.WaveState
import org.rsmod.content.minigames.colosseum.enemies.ColosseumEnemyVarns
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
internal class VolatilityModifier
@Inject
constructor(
    private val waves: ColosseumWaves,
    private val modifiers: ColosseumModifiers,
    private val npcRepo: NpcRepository,
    private val worldRepo: WorldRepository,
    private val worldQueues: WorldQueueList,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val hitModifier: StandardPlayerHitModifier,
    private val sand: ColosseumSand,
) {
    private val gibs by lazy { ColosseumEnemiesRow.all().associateBy { it.npc.id } }

    fun onEnemyDeath(state: WaveState, enemy: Npc) {
        if (gibs[enemy.type.id] == null) return
        val player = waves.playerOf(state) ?: return
        if (modifiers.level(player, Modifier.Volatility) <= 0 || player.level != enemy.level) return
        val direction = Direction.between(enemy.bounds(), player.bounds())
        enemy.vars[ColosseumEnemyVarns.GIB_DIRECTION] = direction.id + 1
    }

    fun onEnemyDespawn(state: WaveState, enemy: Npc) {
        val player = waves.playerOf(state) ?: return
        val level = modifiers.level(player, Modifier.Volatility)
        if (level <= 0) return
        val gib = gibs[enemy.type.id] ?: return
        val size = enemy.type.size
        val southWest = enemy.coords
        val centre = VolatilityRules.centre(southWest, size)
        val radius = VolatilityRules.radius(level)
        val template = waves.templateOf(state, southWest) ?: return
        val direction = Direction[enemy.vars[ColosseumEnemyVarns.GIB_DIRECTION] - 1]
        waves.spawnStatic(state, gib.gib.internalName, template, direction)?.let { spawned ->
            direction?.let { spawned.infoProtocol.setFaceAngle(it.angle, instant = true) }
            spawned.anim(gib.gibSeq.internalName)
            worldQueues.add(GIB_DURATION) {
                if (spawned.isSlotAssigned) npcRepo.del(spawned, Int.MAX_VALUE)
            }
        }
        worldRepo.spotanimMap(SpotanimType(gib.explosion), centre)
        worldRepo.soundArea(
            centre,
            explosionSynth(level),
            radius = EXPLOSION_SOUND_RADIUS,
            size = EXPLOSION_SOUND_SIZE,
        )
        for ((tile, delay) in VolatilityRules.blast(southWest, size, radius)) {
            if (collision.isWalkBlocked(tile)) continue
            worldRepo.spotanimMap(spotanim(BLAST_SPOTANIM), tile, delay = delay)
        }
        if (
            player.hitpoints > 0 && VolatilityRules.inBlast(southWest, size, radius, player.coords)
        ) {
            player.queueHit(1, HitType.Typeless, random.of(BLAST_DAMAGE), hitModifier)
        }
        if (level >= MOLTEN_LEVEL && !collision.isWalkBlocked(centre)) {
            val permanent = modifiers.level(player, Modifier.Reentry) >= PERMANENT_REENTRY_LEVEL
            sand.add(state, player, centre, POOL, permanent, replace = true)
        }
    }

    private fun explosionSynth(level: Int): String =
        if (level >= RADIUS_LEVEL) EXPLOSION_SYNTH_OUTER else EXPLOSION_SYNTH

    private fun spotanim(name: String): SpotanimType = SpotanimType(name.asRSCM(RSCMType.SPOTANIM))

    private companion object {
        private const val GIB_DURATION = 3
        private const val BLAST_SPOTANIM = "spotanim.fireblast_impact"
        private const val POOL = "loc.colosseum_molten_pool_1"
        private const val EXPLOSION_SYNTH = "synth.colosseum_volatility_explosion"
        private const val EXPLOSION_SYNTH_OUTER = "synth.colosseum_volatility_explosion_2"
        private const val EXPLOSION_SOUND_RADIUS = 8
        private const val EXPLOSION_SOUND_SIZE = 6
        private const val RADIUS_LEVEL = 2
        private const val MOLTEN_LEVEL = 3
        private const val PERMANENT_REENTRY_LEVEL = 2
        private val BLAST_DAMAGE = 15..25
    }
}
