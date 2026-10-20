package org.rsmod.content.minigames.colosseum

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.table.ColosseumEnemiesRow
import org.rsmod.api.table.ColosseumSpawnsRow
import org.rsmod.api.table.ColosseumWavesRow
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.map.CoordGrid
import org.rsmod.map.util.Bounds
import org.rsmod.routefinder.collision.CollisionFlagMap

internal class WaveState(val playerUuid: Long?, val session: InstanceSession) {
    val enemies = mutableSetOf<Npc>()
    val extras = mutableSetOf<Npc>()
    var minimus: Npc? = null
    var wave = 0
    var reinforced = false
}

@Singleton
internal class ColosseumWaves
@Inject
constructor(
    private val instances: InstanceManager,
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val mapClock: MapClock,
    private val random: GameRandom,
    private val interactions: AiPlayerInteractions,
    private val modifiers: ColosseumModifiers,
    private val sand: ColosseumSand,
    private val collision: CollisionFlagMap,
) {
    private val states = mutableMapOf<InstanceId, WaveState>()
    private val spawnHooks = mutableListOf<(Npc) -> Unit>()
    private val openHooks = mutableListOf<(WaveState) -> Unit>()
    private val closeHooks = mutableListOf<(WaveState) -> Unit>()
    private val despawnHooks = mutableListOf<(WaveState, Npc) -> Unit>()

    fun onEnemyDespawn(hook: (WaveState, Npc) -> Unit) {
        despawnHooks += hook
    }

    fun enemyDespawned(state: WaveState, npc: Npc) {
        despawnHooks.forEach { it(state, npc) }
    }

    fun onClose(hook: (WaveState) -> Unit) {
        closeHooks += hook
    }

    fun onOpen(hook: (WaveState) -> Unit) {
        openHooks += hook
    }

    fun onEnemySpawn(hook: (Npc) -> Unit) {
        spawnHooks += hook
    }

    fun open(player: Player, session: InstanceSession): WaveState {
        val state = WaveState(player.uuid, session)
        states[session.id] = state
        openHooks.forEach { it(state) }
        return state
    }

    fun close(session: InstanceSession) {
        close(session.id)
    }

    fun close(id: InstanceId) {
        val state = states.remove(id) ?: return
        closeHooks.forEach { it(state) }
        sand.discard(state)
        (state.enemies + state.extras).forEach {
            if (it.isSlotAssigned) npcRepo.del(it, Int.MAX_VALUE)
        }
        state.minimus?.let { if (it.isSlotAssigned) npcRepo.del(it, Int.MAX_VALUE) }
    }

    fun stateOf(player: Player): WaveState? =
        instances.sessionForPlayer(player)?.let { states[it.id] }

    fun stateOf(npc: Npc): WaveState? = instances.instanceForNpc(npc)?.let { states[it] }

    fun playerOf(state: WaveState): Player? =
        playerList.firstOrNull {
            it.uuid == state.playerUuid && instances.sessionForPlayer(it)?.id == state.session.id
        }

    fun resolve(state: WaveState, template: CoordGrid): CoordGrid? =
        instances.resolveCoord(state.session, template)

    fun templateOf(state: WaveState, tile: CoordGrid): CoordGrid? {
        val template = instances.templateCoord(state.session, tile) ?: return null
        val inArena =
            template.level == 0 && (template.mx shl 8 or template.mz) == ColosseumArena.REGION
        return if (inArena) template else null
    }

    fun spawnMinimus(
        state: WaveState,
        template: CoordGrid,
        faceTemplate: CoordGrid,
        intro: Boolean,
    ): Npc? {
        despawnMinimus(state)
        val tile = resolve(state, template) ?: return null
        val npc = spawn(state, ColosseumNpcs.MINIMUS, tile) ?: return null
        npc.mode = NpcMode.None
        resolve(state, faceTemplate)?.let { npc.faceSquare(it) }
        if (intro) npc.anim(SPAWN_SEQ_HUMAN_READY)
        state.minimus = npc
        return npc
    }

    fun despawnMinimus(state: WaveState) {
        val minimus = state.minimus ?: return
        if (minimus.isSlotAssigned) npcRepo.del(minimus, Int.MAX_VALUE)
        state.minimus = null
    }

    fun spawnWave(player: Player, state: WaveState, wave: Int) {
        state.wave = wave
        state.reinforced = false
        val row = ColosseumWavesRow.all().first { it.wave == wave }
        val playerTemplate = templateOf(state, player.coords) ?: ColosseumArena.WALK_IN_END
        if (row.warband) spawnWarband(player, state, playerTemplate)
        val points =
            spawnPoints()
                .filter { it.chebyshevDistance(playerTemplate) >= MIN_SPAWN_DISTANCE }
                .toMutableList()
        for (type in row.enemies) {
            val point =
                if (points.isEmpty()) spawnPoints().first()
                else points.removeAt(random.of(points.size))
            spawnEnemy(player, state, type, point)
            if (type.isShockwave() && modifiers.owns(player, Modifier.DynamicDuo)) {
                spawnEnemy(player, state, type, partnerTile(state, point))
            }
        }
    }

    fun reinforce(player: Player, state: WaveState) {
        if (state.reinforced || state.enemies.isEmpty()) return
        state.reinforced = true
        val row = ColosseumWavesRow.all().first { it.wave == state.wave }
        val playerTemplate = templateOf(state, player.coords) ?: return
        val gates = reinforcementGates()
        val gate =
            if (playerTemplate.z >= NORTH_GATE_MIN_PLAYER_Z) gates.maxBy { it.z }
            else gates.minBy { it.z }
        val lanes = (0 until REINFORCEMENT_LANES).toMutableList()
        val types =
            row.reinforcements.map { type ->
                if (
                    type.id == npcId(ColosseumNpcs.MINOTAUR) &&
                        modifiers.owns(player, Modifier.RedFlag)
                )
                    npcType(ColosseumNpcs.MINOTAUR_ROUTEFIND)
                else type
            }
        var anchor: Bounds? = null
        for (type in types.sortedBy { it.id == npcId(ColosseumNpcs.SHAMAN) }) {
            val tile = gate.translateX(lanes.removeAt(random.of(lanes.size)))
            if (
                type.id == npcId(ColosseumNpcs.SHAMAN) &&
                    anchor?.isWithinDistance(Bounds(playerTemplate), 1) == true
            ) {
                spawnEnemy(player, state, type, spawnRow(SHAMAN_FALLBACK_ROW).coords.first())
                continue
            }
            if (anchor == null) anchor = Bounds(tile, type.size)
            spawnEnemy(player, state, type, tile)
        }
    }

    fun onEnemyRemoved(state: WaveState, npc: Npc): Player? {
        if (!state.enemies.remove(npc)) return null
        if (state.enemies.isNotEmpty()) return null
        return playerOf(state)
    }

    fun clearExtras(state: WaveState) {
        state.extras.forEach { if (it.isSlotAssigned) npcRepo.del(it, Int.MAX_VALUE) }
        state.extras.clear()
    }

    fun trackExtra(state: WaveState, npc: Npc) {
        state.extras += npc
    }

    fun spawnExtra(
        state: WaveState,
        type: String,
        template: CoordGrid,
        direction: Direction? = null,
    ): Npc? {
        val tile = resolve(state, template) ?: return null
        val npc = spawn(state, type, tile, direction) ?: return null
        state.extras += npc
        return npc
    }

    fun spawnWaveEnemy(player: Player, state: WaveState, type: String, template: CoordGrid) {
        spawnEnemy(player, state, npcType(type), template)
    }

    fun spawnStatic(
        state: WaveState,
        type: String,
        template: CoordGrid,
        direction: Direction? = null,
    ): Npc? {
        val tile = resolve(state, template) ?: return null
        return spawn(state, type, tile, direction)
    }

    fun solarFlareTiles(): List<CoordGrid> = spawnRow(SOLAR_FLARES_ROW).coords

    private fun spawnWarband(player: Player, state: WaveState, playerTemplate: CoordGrid) {
        val anchor = warbandAnchor(playerTemplate.level)
        spawnEnemy(player, state, npcType(ColosseumNpcs.BERSERKER), anchor)
        spawnEnemy(player, state, npcType(ColosseumNpcs.ARCHER), anchor.translate(-1, -1))
        spawnEnemy(player, state, npcType(ColosseumNpcs.SEER), anchor.translate(1, -1))
        if (modifiers.owns(player, Modifier.Quartet)) {
            val extra = WARBAND_MEMBERS[random.of(WARBAND_MEMBERS.size)]
            spawnEnemy(player, state, npcType(extra), anchor.translate(0, -2))
        }
    }

    private fun warbandAnchor(level: Int): CoordGrid {
        val (southWest, northEast) = ColosseumArena.warbandBox()
        val width = northEast.x - southWest.x + 1
        val depth = northEast.z - southWest.z + 1
        return CoordGrid(southWest.x + random.of(width), southWest.z + random.of(depth), level)
    }

    private fun partnerTile(state: WaveState, point: CoordGrid): CoordGrid {
        val open =
            PARTNER_RING.map { (dx, dz) -> point.translate(dx, dz) }
                .filter { template ->
                    resolve(state, template)?.let { !collision.isWalkBlocked(it) } == true
                }
        return if (open.isEmpty()) point.translateX(PARTNER_DISTANCE)
        else open[random.of(open.size)]
    }

    private fun spawnEnemy(
        player: Player,
        state: WaveState,
        type: NpcServerType,
        template: CoordGrid,
    ) {
        val tile = resolve(state, template) ?: return
        val npc = spawn(state, type, tile) ?: return
        npc.faceSquare(tile.translateZ(-1))
        spawnSeq(type)?.let { npc.anim(it) }
        state.enemies += npc
        spawnHooks.forEach { it(npc) }
        if (type.id == npcId(ColosseumNpcs.JAGUAR)) npc.opPlayer2(player, interactions)
        else npc.apPlayer2(player, interactions)
    }

    private fun spawn(
        state: WaveState,
        type: String,
        tile: CoordGrid,
        direction: Direction? = null,
    ): Npc? {
        val npcType = ServerCacheManager.getNpc(type.asRSCM(RSCMType.NPC)) ?: return null
        return spawn(state, npcType, tile, direction)
    }

    private fun spawn(
        state: WaveState,
        type: NpcServerType,
        tile: CoordGrid,
        direction: Direction? = null,
    ): Npc {
        val npc = Npc(type, tile)
        direction?.let { npc.respawnDir = it }
        npcRepo.add(npc, Int.MAX_VALUE)
        npc.respawns = false
        instances.attachNpc(state.session.id, npc)
        return npc
    }

    private fun spawnSeq(type: NpcServerType): String? =
        ColosseumEnemiesRow.all().firstOrNull { it.npc.id == type.id }?.spawnSeq?.internalName

    private fun NpcServerType.isShockwave(): Boolean = id == npcId(ColosseumNpcs.SHOCKWAVE)

    private fun spawnPoints(): List<CoordGrid> = spawnRow(SPAWN_POINTS_ROW).coords

    private fun reinforcementGates(): List<CoordGrid> = spawnRow(GATES_ROW).coords

    private fun spawnRow(row: String): ColosseumSpawnsRow = ColosseumSpawnsRow.getRow(row)

    private fun npcType(name: String): NpcServerType =
        checkNotNull(ServerCacheManager.getNpc(npcId(name)))

    private fun npcId(name: String): Int = name.asRSCM(RSCMType.NPC)

    fun now(): Int = mapClock.cycle

    companion object {
        private val WARBAND_MEMBERS =
            listOf(ColosseumNpcs.BERSERKER, ColosseumNpcs.ARCHER, ColosseumNpcs.SEER)

        private const val SPAWN_POINTS_ROW = "dbrow.colosseum_spawn_points"
        private const val SOLAR_FLARES_ROW = "dbrow.colosseum_solar_flares"
        private const val GATES_ROW = "dbrow.colosseum_reinforcement_gates"

        const val SPAWN_SEQ_HUMAN_READY = "seq.npc_human_ready_spawn"
        private const val REINFORCEMENT_LANES = 3
        private const val NORTH_GATE_MIN_PLAYER_Z = 3106
        private const val MIN_SPAWN_DISTANCE = 5
        private const val SHAMAN_FALLBACK_ROW = "dbrow.colosseum_reinforcement_shaman_fallback"

        private const val PARTNER_DISTANCE = 2
        private val PARTNER_RING =
            (-PARTNER_DISTANCE..PARTNER_DISTANCE)
                .flatMap { dx -> (-PARTNER_DISTANCE..PARTNER_DISTANCE).map { dz -> dx to dz } }
                .filter { (dx, dz) -> maxOf(abs(dx), abs(dz)) == PARTNER_DISTANCE }
    }
}
