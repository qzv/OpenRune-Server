package org.rsmod.content.minigames.colosseum.modifiers

import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.Collections
import java.util.IdentityHashMap
import org.rsmod.api.player.disableOverheadPrayers
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.overheadProtectionPrayerVarbits
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.content.minigames.colosseum.ColosseumArena
import org.rsmod.content.minigames.colosseum.ColosseumModifiers
import org.rsmod.content.minigames.colosseum.ColosseumNpcs
import org.rsmod.content.minigames.colosseum.ColosseumWaves
import org.rsmod.content.minigames.colosseum.Modifier
import org.rsmod.content.minigames.colosseum.RunState
import org.rsmod.content.minigames.colosseum.WaveState
import org.rsmod.content.minigames.colosseum.enemies.ColosseumEnemyVarns
import org.rsmod.content.minigames.colosseum.enemies.ColosseumEnemyVarns.setTickVarn
import org.rsmod.content.minigames.colosseum.enemies.ColosseumEnemyVarns.tickVarn
import org.rsmod.content.minigames.colosseum.runState
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

@Singleton
internal class SolarflareModifier
@Inject
constructor(
    private val waves: ColosseumWaves,
    private val modifiers: ColosseumModifiers,
    private val worldQueues: WorldQueueList,
    private val mapClock: MapClock,
    private val random: GameRandom,
    private val doom: DoomModifier,
) {
    private val flareHits: MutableSet<Hit> = Collections.newSetFromMap(IdentityHashMap())

    fun isFlareHit(hit: Hit): Boolean = hit in flareHits

    fun onWaveStart(player: Player, state: WaveState) {
        if (state.wave == ColosseumModifiers.SOL_WAVE) return
        val level = modifiers.level(player, Modifier.Solarflare)
        if (level <= 0) return
        spawn(player, state, waves.solarFlareTiles(), level)
    }

    fun onSolSpawn(player: Player, state: WaveState) {
        val level = modifiers.level(player, Modifier.Solarflare)
        if (level <= 0) return
        val wave = state.wave
        worldQueues.add(SOL_SPAWN_DELAY) {
            if (waves.stateOf(player) !== state || state.wave != wave) return@add
            spawn(player, state, ColosseumArena.coords(SOL_TILES_ROW), level)
        }
    }

    fun onWaveEnd() {
        flareHits.clear()
    }

    private fun spawn(player: Player, state: WaveState, templates: List<CoordGrid>, level: Int) {
        for (template in templates) {
            val flare = waves.spawnExtra(state, ColosseumNpcs.SOLAR_FLARE, template) ?: continue
            flare.mode = NpcMode.None
            flare.moveRestrict = MoveRestrict.PassThru
            flare.defaultMoveSpeed =
                if (level >= FlareOrbit.WALK_LEVEL) MoveSpeed.Walk else MoveSpeed.Crawl
            orbit(
                flare,
                FlareOrbit.firstHeading(template),
                step = 0,
                level,
                delay = FIRST_STEP_DELAY,
            )
            watch(player, state, flare, level)
        }
    }

    private fun orbit(flare: Npc, first: Heading, step: Int, level: Int, delay: Int) {
        worldQueues.add(delay) {
            if (!flare.isSlotAssigned) return@add
            val heading = FlareOrbit.heading(first, step)
            flare.walk(flare.coords.translate(heading.dx, heading.dz))
            flare.setTickVarn(ColosseumEnemyVarns.FLARE_STEP, mapClock.cycle)
            orbit(flare, first, step + 1, level, FlareOrbit.delayAfter(step, level))
        }
    }

    private fun watch(player: Player, state: WaveState, flare: Npc, level: Int) {
        worldQueues.add(1) {
            if (!flare.isSlotAssigned || waves.stateOf(player) !== state) return@add
            val sinceStep =
                flare.tickVarn(ColosseumEnemyVarns.FLARE_STEP)?.let { mapClock.cycle - it }
                    ?: Int.MAX_VALUE
            val active = player.runState == RunState.WaveActive && player.hitpoints > 0
            if (active && FlareOrbit.touches(flare.coords, player.coords, level, sinceStep))
                contact(player, level)
            watch(player, state, flare, level)
        }
    }

    fun disableProtection(player: Player) {
        if (overheadProtectionPrayerVarbits.none { player.vars[it] == 1 }) return
        player.disableOverheadPrayers()
        player.mes(PRAYER_DISABLED_MESSAGE)
    }

    private fun contact(player: Player, level: Int) {
        if (level >= FlareOrbit.WALK_LEVEL) player.softQueue(FlareOrbit.PRAYER_QUEUE, 1)
        val hit =
            player.queueHit(HIT_DELAY, HitType.Typeless, random.of(DAMAGE), NoopPlayerHitModifier)
        flareHits += hit
        worldQueues.add(HIT_DELAY + FORGET_DELAY) { flareHits -= hit }
        doom.onDamage(player)
    }

    private companion object {
        private const val SOL_TILES_ROW = "dbrow.colosseum_sol_flare_tiles"
        private const val SOL_SPAWN_DELAY = 3
        private const val FIRST_STEP_DELAY = 1
        private const val HIT_DELAY = 2
        private const val FORGET_DELAY = 2
        private const val PRAYER_DISABLED_MESSAGE =
            "<col=ff3045>The Solar Flare disables your protection prayer!</col>"
        private val DAMAGE = 10..20
    }
}
