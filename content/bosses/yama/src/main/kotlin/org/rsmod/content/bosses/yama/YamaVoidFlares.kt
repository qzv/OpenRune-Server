package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.types.AttackType
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.table.YamaFlareTilesRow
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

internal data class ArenaOffset(val dx: Int, val dz: Int)

internal class FlareState(
    val npc: Npc,
    val tile: CoordGrid,
    val element: YamaElement,
    val explodeTick: Int,
    val maxHp: Int,
    val finalPhase: Boolean,
) {
    var settled = false
}

@Singleton
internal class YamaVoidFlares
@Inject
constructor(private val deps: BossDeps, private val attackTypes: AttackTypes) {
    private val states = HashMap<Npc, FlareState>()

    private val continuousCursors = WeakHashMap<YamaFight, Int>()

    fun registerHandlers(ctx: ScriptContext) {
        with(ctx) {
            val type = ServerCacheManager.getNpc(FLARE_NPC.asRSCM(RSCMType.NPC)) ?: return
            onNpcQueue(type, "queue.death") { slain(npc) }
            onModifyNpcHit(type) { guaranteeMaxHit(this) }
        }
    }

    private fun guaranteeMaxHit(event: NpcHitEvents.Modify) {
        val hit = event.hit
        if (!hit.isFromPlayer || event.npc.vars[GUARANTEED_HIT] == 1) return
        val player = hit.sourceSlot?.let { deps.playerList[it] } ?: return
        val standardRanged =
            hit.type == HitType.Ranged && attackTypes.get(player) == AttackType.Standard
        if (hit.type == HitType.Magic || standardRanged) hit.damage = player.vars[PLAYER_MAX_HIT]
    }

    fun spawnEarly(fight: YamaFight) {
        deps.worldQueues.add(EARLY_SPAWN_DELAY) {
            val charge = if (fight.contract != null) CONTRACT_EARLY_CHARGE else EARLY_CHARGE
            if (fight.contract?.fourFlares == true) {
                spawn(fight, FINAL_TILES, FINAL_TILES.size, charge, false, halfPair = true)
                return@add
            }
            val pair = if (fight.phase >= SECOND_PHASE) PAIR_TWO else PAIR_ONE
            spawn(fight, pair, pair.size, charge, false)
        }
    }

    fun spawnPair(fight: YamaFight) = spawn(fight, FINAL_TILES, PAIR_SIZE, FINAL_CHARGE, true)

    fun spawnBurst(fight: YamaFight) =
        spawn(fight, FINAL_TILES, FINAL_TILES.size, FINAL_CHARGE, true)

    fun spawnMelee(fight: YamaFight) = spawn(fight, FINAL_TILES, 1, MELEE_CHARGE, true)

    private fun spawnContinuous(fight: YamaFight) {
        val start = continuousCursors.getOrPut(fight) { deps.random.of(CONTINUOUS_CYCLE.size) }
        val free =
            CONTINUOUS_CYCLE.indices
                .map { (start + it) % CONTINUOUS_CYCLE.size }
                .firstOrNull { index -> isFree(fight, CONTINUOUS_CYCLE[index]) }
                ?: return
        continuousCursors[fight] = (free + 1) % CONTINUOUS_CYCLE.size
        spawn(fight, listOf(CONTINUOUS_CYCLE[free]), 1, FINAL_CHARGE, true, CONTINUOUS_HP)
    }

    private fun isFree(fight: YamaFight, offset: ArenaOffset): Boolean =
        fight.flares.none { !it.settled && it.tile == tileOf(offset) }

    private fun spawn(
        fight: YamaFight,
        candidates: List<ArenaOffset>,
        count: Int,
        chargeTicks: Int,
        finalPhase: Boolean,
        health: Int? = null,
        halfPair: Boolean = false,
    ) {
        val type = ServerCacheManager.getNpc(FLARE_NPC.asRSCM(RSCMType.NPC)) ?: return
        val now = deps.mapClock.cycle
        val fireFirst = deps.random.of(2) == 0
        val pool = candidates.toMutableList()
        val free = ArrayList<ArenaOffset>()
        while (pool.isNotEmpty() && free.size < count) {
            val offset = pool.removeAt(deps.random.of(pool.size))
            if (isFree(fight, offset)) free += offset
        }
        free.forEachIndexed { index, offset ->
            val laterHalf = halfPair && index >= HALF_PAIR_START
            val element =
                when {
                    halfPair -> if (laterHalf) YamaElement.SHADOW else YamaElement.FIRE
                    finalPhase -> YamaElement.SHADOW
                    (index == 0) == fireFirst -> YamaElement.FIRE
                    else -> YamaElement.SHADOW
                }
            val tile = tileOf(offset)
            val npc = Npc(type, fight.instance(tile.x, tile.z))
            deps.npcRepo.add(npc, Int.MAX_VALUE)
            npc.hideAllOps()
            if (element.flareModels.isNotEmpty()) npc.setBodyModels(element.flareModels)
            val base = health ?: if (finalPhase) FINAL_PHASE_HP else type.hitpoints
            val hp = if (laterHalf) base / 2 else base
            npc.hitpoints = hp
            if (fight.contract?.flaresAlwaysHit == true) npc.vars[GUARANTEED_HIT] = 1
            npc.anim(SPAWN_SEQ)
            val explode = now + CHARGE_START + chargeTicks + EXPLODE_AFTER_BAR
            val state = FlareState(npc, tile, element, explode, hp, finalPhase)
            fight.flares += state
            states[npc] = state
            deps.worldQueues.add(CHARGE_START) {
                if (!state.settled) showBars(npc, chargeTicks)
            }
        }
    }

    private fun showBars(npc: Npc, chargeTicks: Int) {
        val charge = CHARGE_HEADBAR.asRSCM(RSCMType.HEADBAR)
        val cycles = chargeTicks * CYCLES_PER_TICK
        npc.showHeadbar(Headbar.fromNoSource(charge, charge, 0, CHARGE_SEGMENTS, 0, cycles))
        val bar = npc.visHeadbar(params.headbar)
        val base = npc.baseHitpointsLvl
        val fill = (npc.hitpoints * bar.segments + base - 1) / base
        npc.showHeadbar(Headbar.fromNoSource(bar.id, bar.id, fill, fill, 0, 0))
        npc.showAllOps()
    }

    fun tick(fight: YamaFight, now: Int) {
        val continuous = fight.contract?.continuousFlares == true && !fight.inJudge
        if (continuous && now % CONTINUOUS_PERIOD == 0) spawnContinuous(fight)
        for (flare in fight.flares.toList()) {
            if (!flare.settled && now == flare.explodeTick) explode(fight, flare, now)
        }
        fight.flares.removeAll { it.settled && it.npc.hitpoints <= 0 && now > it.explodeTick }
    }

    fun clear(fight: YamaFight, delay: Int) {
        for (flare in fight.flares.toList()) {
            if (flare.settled) continue
            flare.settled = true
            deps.worldQueues.add(delay) { remove(flare) }
        }
        fight.flares.clear()
    }

    private fun tileOf(offset: ArenaOffset): CoordGrid =
        CoordGrid(YamaPhases.SPAWN.x + offset.dx, YamaPhases.SPAWN.z + offset.dz)

    private fun explode(fight: YamaFight, flare: FlareState, now: Int) {
        flare.settled = true
        val players = fight.players.toList()
        val yama = fight.yama ?: return
        flare.npc.anim(EXPLODE_SEQ)
        flare.npc.spotanim(if (flare.element == YamaElement.FIRE) RED_EXPLOSION else BLUE_EXPLOSION)
        deps.worldQueues.add(REMOVE_AFTER_EXPLOSION) { remove(flare) }
        val immune = players.filter { fight.isImmune(it, flare.element, now) && !flare.finalPhase }
        if (immune.isNotEmpty()) {
            deps.worldQueues.add(1) {
                if (!yama.isSlotAssigned) return@add
                for (player in immune.filter { it in fight.players }) absorb(player, yama, flare)
            }
        }
        if (immune.size == players.size) return
        val remaining = max(0, flare.npc.hitpoints).toDouble() / flare.maxHp
        val full =
            if (flare.finalPhase) {
                deps.random.of(FINAL_MIN_HIT, FINAL_MAX_HIT)
            } else {
                deps.random.of(EARLY_MIN_HIT, EARLY_MAX_HIT)
            }
        val damage = (full * sqrt(remaining)).roundToInt()
        deps.hitpointsRestorer.heal(yama, damage * HEAL_MULTIPLIER, showHitsplat = true)
        val lethal = fight.contract?.lethalFlares == true
        deps.worldQueues.add(1) {
            if (!yama.isSlotAssigned) return@add
            for (player in players.filter { it in fight.players && it !in immune }) {
                val dealt = if (lethal) player.hitpoints else damage
                player.finishNpcHit(yama, 1, HitType.Typeless, dealt, deps.playerHitModifier)
            }
        }
    }

    private fun absorb(player: Player, yama: Npc, flare: FlareState) {
        player.queueHit(
            yama,
            1,
            HitType.Typeless,
            0,
            deps.playerHitModifier,
            YamaStrikes.ABSORBED_HITMARK,
        )
        player.mes(flare.element.absorbMessage(), ChatType.Spam)
    }

    private fun slain(npc: Npc) {
        val state = states[npc] ?: return
        if (state.settled) return
        state.settled = true
        npc.anim(DEATH_SEQ)
        deps.worldQueues.add(REMOVE_AFTER_DEATH) { remove(state) }
    }

    private fun remove(flare: FlareState) {
        states.remove(flare.npc)
        if (flare.npc.isSlotAssigned) deps.npcRepo.del(flare.npc, Int.MAX_VALUE)
    }

    private companion object {
        private const val FLARE_NPC = "npc.yama_voidflare"
        private const val GUARANTEED_HIT = "varn.guaranteed_hit"
        private const val PLAYER_MAX_HIT = "varp.com_maxhit"
        private const val SPAWN_SEQ = "seq.npc_voidflare_spawn"
        private const val EXPLODE_SEQ = "seq.npc_voidflare_explode"
        private const val DEATH_SEQ = "seq.npc_voidflare_death"
        private const val RED_EXPLOSION = "spotanim.vfx_voidflare_explode_red"
        private const val BLUE_EXPLOSION = "spotanim.vfx_voidflare_explode_blue"
        private const val CHARGE_HEADBAR = "headbar.yama_voidflare_charge"
        private const val CHARGE_SEGMENTS = 30
        private const val CYCLES_PER_TICK = 30
        private const val CHARGE_START = 3
        private const val EXPLODE_AFTER_BAR = 2
        private const val REMOVE_AFTER_EXPLOSION = 2
        private const val REMOVE_AFTER_DEATH = 3
        private const val FINAL_PHASE_HP = 71
        private const val CONTINUOUS_HP = 81
        private const val CONTINUOUS_PERIOD = 20
        private const val HALF_PAIR_START = 2
        private const val EARLY_MIN_HIT = 42
        private const val EARLY_MAX_HIT = 45
        private const val FINAL_MIN_HIT = 20
        private const val FINAL_MAX_HIT = 23
        private const val HEAL_MULTIPLIER = 3
        private const val EARLY_SPAWN_DELAY = 2
        private const val SECOND_PHASE = 2
        private const val EARLY_CHARGE = 21
        private const val CONTRACT_EARLY_CHARGE = 20
        private const val FINAL_CHARGE = 20
        private const val MELEE_CHARGE = 12
        private const val PAIR_SIZE = 2
        private val PAIR_ONE by lazy { pair(1) }
        private val PAIR_TWO by lazy { pair(2) }
        private val FINAL_TILES by lazy { PAIR_ONE + PAIR_TWO }
        private val CONTINUOUS_CYCLE by lazy {
            YamaFlareTilesRow.all().sortedBy { it.cycleOrder }.map { ArenaOffset(it.dx, it.dz) }
        }

        private fun pair(pair: Int): List<ArenaOffset> =
            YamaFlareTilesRow.all().filter { it.pair == pair }.map { ArenaOffset(it.dx, it.dz) }
    }
}
