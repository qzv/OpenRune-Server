package org.rsmod.content.minigames.colosseum.modifiers

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.hit.NpcDamageContributor
import org.rsmod.api.npc.hit.NpcHitpointsRestorer
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.content.minigames.colosseum.ColosseumModifiers
import org.rsmod.content.minigames.colosseum.ColosseumNpcs
import org.rsmod.content.minigames.colosseum.ColosseumWaves
import org.rsmod.content.minigames.colosseum.Modifier
import org.rsmod.content.minigames.colosseum.WaveState
import org.rsmod.content.minigames.colosseum.enemies.ColosseumEnemyVarns
import org.rsmod.content.minigames.colosseum.enemies.ColosseumEnemyVarns.setTickVarn
import org.rsmod.content.minigames.colosseum.enemies.ColosseumEnemyVarns.tickVarn
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

internal class Totem(val npc: Npc, val enemy: Npc, val spawnedAt: Int) {
    var readyAt = spawnedAt + TotemicModifier.FIRST_ATTACK_DELAY
    var impactAt = -1
}

@Singleton
internal class TotemicModifier
@Inject
constructor(
    private val waves: ColosseumWaves,
    private val modifiers: ColosseumModifiers,
    private val worldRepo: WorldRepository,
    private val worldQueues: WorldQueueList,
    private val collision: CollisionFlagMap,
    private val npcRepo: NpcRepository,
    private val rayCast: RayCastValidator,
    private val mapClock: MapClock,
    private val hitpointsRestorer: NpcHitpointsRestorer,
) {
    private val totems = mutableMapOf<WaveState, MutableList<Totem>>()
    private val solId by lazy { ColosseumNpcs.SOL.asRSCM(RSCMType.NPC) }

    fun onEnemyDamaged(enemy: Npc, player: Player, damage: Int) {
        if (damage <= 0) return
        val state = waves.stateOf(enemy) ?: return
        if (enemy !in state.enemies && enemy.type.id != solId) return
        if (!modifiers.owns(player, Modifier.Totemic)) return
        if (enemy.hitpoints <= 0 || enemy.hitpoints * 2 >= enemy.baseHitpointsLvl) return
        val existing = totems[state].orEmpty().filter { it.enemy === enemy }
        if (existing.isNotEmpty()) {
            existing.forEach(::attack)
            return
        }
        val now = mapClock.cycle
        val lastSpawn = enemy.tickVarn(ColosseumEnemyVarns.TOTEM_SPAWN)
        if (lastSpawn != null && now - lastSpawn < SPAWN_COOLDOWN) return
        spawn(state, enemy, now) ?: return
        if (enemy.type.id == solId && enemy.vars[ColosseumEnemyVarns.TOTEM_TIMER] == 0) {
            enemy.vars[ColosseumEnemyVarns.TOTEM_TIMER] = 1
            scheduleSolTotem(state, enemy)
        }
    }

    private fun scheduleSolTotem(state: WaveState, sol: Npc) {
        worldQueues.add(SOL_EXTRA_TOTEM_INTERVAL) {
            if (!sol.isSlotAssigned || sol.hitpoints <= 0 || waves.stateOf(sol) !== state) {
                sol.vars[ColosseumEnemyVarns.TOTEM_TIMER] = 0
                return@add
            }
            spawn(state, sol, mapClock.cycle)
            scheduleSolTotem(state, sol)
        }
    }

    fun onTotemDestroyed(totem: Npc) {
        for (owned in totems.values) {
            owned.removeAll { it.npc === totem }
        }
    }

    fun onWaveEnd(state: WaveState) {
        totems.remove(state)?.forEach {
            it.enemy.vars[ColosseumEnemyVarns.TOTEM_SPAWN] = 0
            it.enemy.vars[ColosseumEnemyVarns.TOTEM_TIMER] = 0
        }
    }

    private fun spawn(state: WaveState, enemy: Npc, now: Int): Totem? {
        val occupied = totems[state].orEmpty().map { it.npc.coords }.toSet()
        val (tile, direction) = totemTile(enemy, occupied) ?: return null
        val template = waves.templateOf(state, tile) ?: return null
        val npc = waves.spawnExtra(state, TOTEM, template, direction) ?: return null
        npc.mode = NpcMode.None
        npc.ignoreCombatInteractions = true
        npc.faceNpc(enemy)
        worldRepo.spotanimMap(spotanim(SPAWN_SPOTANIM), tile)
        enemy.setTickVarn(ColosseumEnemyVarns.TOTEM_SPAWN, now)
        val totem = Totem(npc, enemy, now)
        totems.getOrPut(state) { mutableListOf() } += totem
        watch(state, totem)
        return totem
    }

    private fun totemTile(enemy: Npc, occupied: Set<CoordGrid>): Pair<CoordGrid, Direction>? {
        val southWest = enemy.coords.translate(-1, -1) to Direction.NorthEast
        val northEast =
            enemy.coords.translate(enemy.type.size, enemy.type.size) to Direction.SouthWest
        return listOf(southWest, northEast).firstOrNull { (tile, _) ->
            tile !in occupied && !collision.isWalkBlocked(tile)
        }
    }

    fun onTotemKilled(totem: Npc) {
        totem.hideAllOps()
        totem.resetFaceEntity()
    }

    private fun watch(state: WaveState, totem: Totem) {
        worldQueues.add(1) {
            if (!totem.npc.isSlotAssigned || totems[state]?.contains(totem) != true) return@add
            if (!totem.enemy.isSlotAssigned || totem.enemy.hitpoints <= 0) {
                totems[state]?.remove(totem)
                npcRepo.del(totem.npc, Int.MAX_VALUE)
                return@add
            }
            if (totem.npc.hitpoints <= 0) return@add
            if (totem.impactAt == mapClock.cycle) land(totem)
            attack(totem)
            watch(state, totem)
        }
    }

    private fun attack(totem: Totem) {
        val enemy = totem.enemy
        val now = mapClock.cycle
        if (
            now < totem.readyAt || enemy.hitpoints <= 0 || enemy.hitpoints >= enemy.baseHitpointsLvl
        )
            return
        val size = enemy.type.size
        if (
            !rayCast.hasLineOfSight(
                totem.npc.coords,
                enemy.coords,
                destWidth = size,
                destLength = size,
            )
        )
            return
        totem.npc.anim(ATTACK_SEQ)
        worldRepo.projAnim(
            ProjAnim(
                spotanim = PROJECTILE.asRSCM(RSCMType.SPOTANIM),
                startHeight = PROJECTILE_START_HEIGHT,
                endHeight = PROJECTILE_END_HEIGHT,
                startTime = PROJECTILE_START_TIME,
                endTime = PROJECTILE_END_TIME,
                angle = 0,
                progress = 0,
                sourceIndex = 0,
                targetIndex = enemy.slotId + 1,
                startCoord = totem.npc.coords,
                endCoord = enemy.coords.translate((size - 1) / 2, (size - 1) / 2),
            )
        )
        totem.impactAt = now + HEAL_FLIGHT_TICKS
        totem.readyAt = now + ATTACK_INTERVAL
    }

    private fun land(totem: Totem) {
        val enemy = totem.enemy
        enemy.spotanim(IMPACT_SPOTANIM)
        hitpointsRestorer.heal(enemy, healAmount(enemy), showHitsplat = true)
    }

    private fun healAmount(enemy: Npc): Int =
        if (enemy.type.id == solId) SOL_HEAL else enemy.baseHitpointsLvl * HEAL_PERCENT / PERCENT

    private fun spotanim(name: String): SpotanimType = SpotanimType(name.asRSCM(RSCMType.SPOTANIM))

    companion object {
        const val TOTEM = "npc.colosseum_healing_totem"
        const val FIRST_ATTACK_DELAY = 7
        private const val SPAWN_SPOTANIM = "spotanim.smokepuff"
        private const val SPAWN_COOLDOWN = 200
        private const val SOL_EXTRA_TOTEM_INTERVAL = 200
        private const val ATTACK_INTERVAL = 6
        private const val HEAL_FLIGHT_TICKS = 6
        private const val ATTACK_SEQ = "seq.npc_colosseum_totem_attack_01"
        private const val PROJECTILE = "spotanim.vfx_colosseum_totem_projectile_01"
        private const val IMPACT_SPOTANIM = "spotanim.vfx_colosseum_totem_impact_01"
        private const val PROJECTILE_START_HEIGHT = 220
        private const val PROJECTILE_END_HEIGHT = 138
        private const val PROJECTILE_START_TIME = 90
        private const val PROJECTILE_END_TIME = 180
        private const val HEAL_PERCENT = 30
        private const val PERCENT = 100
        private const val SOL_HEAL = 75
    }
}

internal class TotemicDamageContributor @Inject constructor(private val totemic: TotemicModifier) :
    NpcDamageContributor {
    override fun onPlayerDamageNpc(npc: Npc, source: Player, damage: Int) {
        if (npc.type.id == totemId && npc.hitpoints <= 0) {
            totemic.onTotemKilled(npc)
            return
        }
        totemic.onEnemyDamaged(npc, source, damage)
    }

    private val totemId by lazy { TotemicModifier.TOTEM.asRSCM(RSCMType.NPC) }
}
