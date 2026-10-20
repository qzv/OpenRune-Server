package org.rsmod.content.minigames.colosseum.modifiers

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.mechanics.toxins.Toxin
import org.rsmod.api.mechanics.toxins.ToxinImmunity
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.minigames.colosseum.ColosseumArena
import org.rsmod.content.minigames.colosseum.ColosseumModifiers
import org.rsmod.content.minigames.colosseum.ColosseumWaves
import org.rsmod.content.minigames.colosseum.Modifier
import org.rsmod.content.minigames.colosseum.RunState
import org.rsmod.content.minigames.colosseum.WaveState
import org.rsmod.content.minigames.colosseum.runState
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

internal class Bee(val npc: Npc, val home: CoordGrid, val player: Player, val level: Int) {
    var age = 0
    var dead = false
    var hidden = false
    var expired = false
    var legTarget: CoordGrid = home
}

@Singleton
internal class BeesModifier
@Inject
constructor(
    private val waves: ColosseumWaves,
    private val modifiers: ColosseumModifiers,
    private val npcRepo: NpcRepository,
    private val worldRepo: WorldRepository,
    private val worldQueues: WorldQueueList,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
) {
    private val swarms = mutableMapOf<WaveState, MutableList<Bee>>()

    fun onWaveStart(player: Player, state: WaveState) {
        if (state.wave == ColosseumModifiers.SOL_WAVE) return
        throwJars(player, state)
    }

    fun onSolSpawn(player: Player, state: WaveState) {
        worldQueues.add(SOL_THROW_DELAY) { if (active(player, state)) throwJars(player, state) }
    }

    fun onWaveEnd(state: WaveState) {
        val bees = swarms[state] ?: return
        val keepsHidden = state.wave == ColosseumModifiers.SOL_WAVE
        bees.removeAll { bee ->
            if (bee.dead && (keepsHidden || !bee.hidden)) {
                bee.expired = true
                false
            } else {
                delete(bee)
                true
            }
        }
    }

    fun onClose(state: WaveState) {
        swarms.remove(state)?.forEach(::delete)
    }

    fun of(npc: Npc): Bee? =
        swarms.values.firstNotNullOfOrNull { bees -> bees.firstOrNull { it.npc === npc } }

    fun onKilled(bee: Bee) {
        bee.dead = true
    }

    fun onHidden(bee: Bee) {
        bee.hidden = true
        bee.npc.infoProtocol.setBodyModel(INVISIBLE_MODEL)
        worldQueues.add(RESPAWN_DELAY) {
            if (!bee.npc.isSlotAssigned) return@add
            if (bee.expired || swarms.values.none { bee in it }) {
                delete(bee)
                swarms.values.forEach { it.remove(bee) }
                return@add
            }
            bee.npc.hitpoints = bee.npc.baseHitpointsLvl
            bee.npc.infoProtocol.resetBodyModel()
            bee.npc.showAllOps()
            bee.dead = false
            bee.hidden = false
            // The bee's own tick node runs after this one on the same cycle and increments the age.
            bee.age = BeeRules.ageAfterRespawn() - 1
        }
    }

    private fun throwJars(player: Player, state: WaveState) {
        val level = modifiers.level(player, Modifier.Bees)
        if (level <= 0) return
        val wave = state.wave
        val playerTemplate = waves.templateOf(state, player.coords) ?: return
        val homes = (1..level).mapNotNull { spawnTile(state, playerTemplate) }
        val soundCentre =
            waves.resolve(state, ColosseumArena.coords(SOUND_CENTRE_ROW).first()) ?: return
        worldRepo.soundArea(soundCentre, THROW_SYNTH, radius = SOUND_RADIUS, size = SOUND_SIZE)
        worldRepo.soundArea(
            soundCentre,
            SHATTER_SYNTH,
            delay = SHATTER_SOUND_DELAY,
            radius = SOUND_RADIUS,
            size = SOUND_SIZE,
        )
        worldRepo.soundArea(
            soundCentre,
            SWARM_SYNTH,
            delay = SWARM_SOUND_DELAY,
            radius = SOUND_RADIUS,
            size = SOUND_SIZE,
        )
        for (home in homes) {
            val tile = waves.resolve(state, home) ?: continue
            worldRepo.projAnim(
                ProjAnim(
                    spotanim = JAR_TRAVEL.asRSCM(RSCMType.SPOTANIM),
                    startHeight = JAR_START_HEIGHT,
                    endHeight = 0,
                    startTime = 0,
                    endTime = JAR_TRAVEL_CYCLES,
                    angle = JAR_ANGLE,
                    progress = JAR_PROGRESS,
                    sourceIndex = 0,
                    targetIndex = 0,
                    startCoord = tile.translateZ(JAR_THROW_OFFSET),
                    endCoord = tile,
                )
            )
            worldRepo.spotanimMap(
                spotanim(JAR_IMPACT),
                tile,
                height = JAR_IMPACT_HEIGHT,
                delay = JAR_TRAVEL_CYCLES,
            )
        }
        worldQueues.add(SPAWN_DELAY) {
            if (!active(player, state) || state.wave != wave) return@add
            homes.forEach { spawn(player, state, it, level) }
        }
    }

    private fun spawnTile(state: WaveState, playerTemplate: CoordGrid): CoordGrid? {
        val tiles =
            (SPAWN_MIN_X..SPAWN_MAX_X)
                .flatMap { x -> (SPAWN_MIN_Z..SPAWN_MAX_Z).map { z -> CoordGrid(x, z, 0) } }
                .filter { footprintClear(state, it) }
        val distant =
            tiles.filter {
                it.chebyshevDistance(playerTemplate) in MIN_PLAYER_DISTANCE..MAX_PLAYER_DISTANCE
            }
        val pool = distant.ifEmpty { tiles }
        return if (pool.isEmpty()) null else pool[random.of(pool.size)]
    }

    private fun footprintClear(state: WaveState, template: CoordGrid): Boolean {
        val tile = waves.resolve(state, template) ?: return false
        return (0 until BeeRules.SIZE).all { dx ->
            (0 until BeeRules.SIZE).all { dz -> !collision.isWalkBlocked(tile.translate(dx, dz)) }
        }
    }

    private fun spawn(player: Player, state: WaveState, template: CoordGrid, level: Int) {
        val npc = waves.spawnStatic(state, BEES, template) ?: return
        npc.mode = NpcMode.None
        npc.ignoreCombatInteractions = true
        npc.defaultMoveSpeed = MoveSpeed.Crawl
        npc.faceSquare(npc.coords.translateZ(-1))
        npc.anim(SPAWN_SEQ)
        val bee = Bee(npc, npc.coords, player, level)
        swarms.getOrPut(state) { mutableListOf() } += bee
        tick(state, bee)
    }

    private fun tick(state: WaveState, bee: Bee) {
        worldQueues.add(1) {
            if (!bee.npc.isSlotAssigned || swarms[state]?.contains(bee) != true) return@add
            if (!bee.dead) {
                bee.age++
                sting(bee)
                move(bee)
            }
            tick(state, bee)
        }
    }

    private fun sting(bee: Bee) {
        val player = bee.player
        if (player.runState != RunState.WaveActive || player.hitpoints <= 0) return
        if (!BeeRules.stings(bee.npc.coords, player.coords)) return
        bee.npc.anim(ATTACK_SEQ)
        val damage = BeeRules.damage(random.of(STING_DAMAGE), bee.level)
        player.queueHit(
            bee.npc,
            1,
            HitType.Typeless,
            damage,
            NoopPlayerHitModifier,
            hitmark = hitmark_groups.poison_damage,
        )
        poison(player)
    }

    private fun poison(player: Player) {
        if (PlayerVenom.isEnvenomed(player) || ToxinImmunity.hasPoisonImmunity(player)) return
        if (PlayerPoison.hasWornPoisonEnvenomImmunity(player)) return
        if (!PlayerPoison.isPoisoned(player)) {
            player.mes(POISONED_MESSAGE)
            player.timer(POISON_TIMER, PlayerPoison.TICK_INTERVAL)
        }
        VarPlayerIntMapSetter.set(player, POISON_SEVERITY, STING_POISON_SEVERITY)
        Toxin.syncStatusOrbs(player)
    }

    private fun move(bee: Bee) {
        if (BeeRules.startsLeg(bee.age))
            bee.legTarget = bee.player.coords.let { BeeRules.legTarget(it, spread(), spread()) }
        if (!BeeRules.steps(bee.age) || bee.npc.coords == bee.legTarget) return
        bee.npc.walk(BeeRules.stepToward(bee.npc.coords, bee.legTarget))
    }

    private fun spread(): Int = random.of(-BeeRules.TARGET_SPREAD, BeeRules.TARGET_SPREAD)

    private fun active(player: Player, state: WaveState): Boolean =
        waves.stateOf(player) === state && player.runState == RunState.WaveActive

    private fun delete(bee: Bee) {
        if (bee.npc.isSlotAssigned) npcRepo.del(bee.npc, Int.MAX_VALUE)
    }

    private fun spotanim(name: String): SpotanimType = SpotanimType(name.asRSCM(RSCMType.SPOTANIM))

    companion object {
        const val BEES = "npc.colosseum_modifier_bees"
        const val DESPAWN_SEQ = "seq.npc_colosseum_bees_despawn_01"

        private const val SPAWN_SEQ = "seq.npc_colosseum_bees_spawn_01"
        private const val ATTACK_SEQ = "seq.npc_colosseum_bees_attack_01"
        private const val JAR_TRAVEL = "spotanim.vfx_colosseum_bee_jar_travel"
        private const val JAR_IMPACT = "spotanim.vfx_colosseum_bee_jar_impact_01"
        private const val THROW_SYNTH = "synth.colosseum_bees_jar_throw"
        private const val SHATTER_SYNTH = "synth.colosseum_bees_jar_shatter"
        private const val SWARM_SYNTH = "synth.colosseum_bees_swarm"
        private const val POISON_SEVERITY = "varp.poison_severity"
        private const val POISON_TIMER = "timer.player_poison"
        private const val POISONED_MESSAGE = "You have been poisoned!"

        private const val SOUND_CENTRE_ROW = "dbrow.colosseum_bees_sound_centre"
        private const val SOUND_RADIUS = 15
        private const val SOUND_SIZE = 15
        private const val SHATTER_SOUND_DELAY = 123
        private const val SWARM_SOUND_DELAY = 143

        private const val JAR_START_HEIGHT = 400
        private const val JAR_TRAVEL_CYCLES = 120
        private const val JAR_ANGLE = 20
        private const val JAR_PROGRESS = 1
        private const val JAR_THROW_OFFSET = 5
        private const val JAR_IMPACT_HEIGHT = 1

        private const val SPAWN_MIN_X = 1818
        private const val SPAWN_MAX_X = 1832
        private const val SPAWN_MIN_Z = 3100
        private const val SPAWN_MAX_Z = 3114
        private const val MIN_PLAYER_DISTANCE = 5
        private const val MAX_PLAYER_DISTANCE = 16

        private const val SPAWN_DELAY = 5
        private const val SOL_THROW_DELAY = 3
        private const val RESPAWN_DELAY = 50
        private const val INVISIBLE_MODEL = 32709
        private val STING_DAMAGE = 5..10
        private const val STING_POISON_SEVERITY = 10
    }
}
