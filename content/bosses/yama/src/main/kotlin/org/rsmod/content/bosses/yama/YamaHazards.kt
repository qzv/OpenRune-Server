package org.rsmod.content.bosses.yama

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.lockOverheads
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.output.spotanimMapPrivate
import org.rsmod.api.player.overheadProtectionPrayerVarbits
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

internal class Streak(val row: Int, val start: Int, val x0: Int, val step: Int, val length: Int)

internal class ActiveWave(val start: Int, val layer: WaveLayer)

internal class ScheduledHazard(
    val tick: Int,
    val started: Boolean = false,
    val action: (Int) -> Unit,
)

@Singleton
internal class YamaHazards @Inject constructor(private val deps: BossDeps) {
    fun phaseStarted(fight: YamaFight, now: Int) {
        val contract = fight.contract
        if (fight.phase >= YamaPhases.FINAL) return
        val opening = fight.layout.opening
        val contractFight = contract != null
        val interval = if (contractFight) CONTRACT_INTERVAL else ATTACK_INTERVAL
        val standards =
            if (contractFight) CONTRACT_STANDARDS else YamaRotation.STANDARDS_PER_SPECIAL
        val streakLead = if (contractFight) CONTRACT_STREAK_LEAD else STREAK_LEAD
        val waveLead = if (contractFight) CONTRACT_WAVE_LEAD else WAVE_LEAD
        val indexes = if (contractFight) CONTRACT_HAZARD_INDEXES else HAZARD_ATTACK_INDEXES
        for (index in indexes) {
            val special = YamaRotation.attack(opening, index, standards)
            val landing = now + interval * index
            if (special.element == YamaElement.FIRE) {
                fight.scheduled +=
                    ScheduledHazard(landing - streakLead) { startStreaks(fight, it) }
            } else {
                fight.scheduled +=
                    ScheduledHazard(landing - waveLead) { startWaves(fight, it) }
            }
        }
    }

    fun launchWavePair(fight: YamaFight, now: Int) {
        WaveLayer.entries.forEach { launchWave(fight, now, it) }
    }

    fun crash(
        fight: YamaFight,
        now: Int,
        sets: Int = YamaCrash.SETS,
        delay: Int = YamaCrash.START_AFTER_ATTACK_TICKS,
        vararg targets: Player,
    ) {
        val victims = if (targets.isEmpty()) fight.players.toList() else targets.toList()
        for (target in victims) {
            val lines =
                if (sets == 1) {
                    listOf(CrashLine.entries[deps.random.of(CrashLine.entries.size)])
                } else {
                    val first =
                        if (deps.random.of(2) == 0) CrashLine.HORIZONTAL else CrashLine.VERTICAL
                    YamaCrash.order(first, sets)
                }
            lines.forEachIndexed { index, line ->
                val start = now + delay + index * YamaCrash.SET_SPACING_TICKS
                fight.scheduled +=
                    ScheduledHazard(start) { crashSet(fight, it, line, target) }
            }
        }
    }

    private fun crashSet(fight: YamaFight, now: Int, line: CrashLine, target: Player) {
        if (target !in fight.players) return
        val centre = target.coords
        target.soundSynth(CRASH_LAUNCH_SOUND, delay = CRASH_LAUNCH_SOUND_DELAY)
        target.soundSynth(CRASH_FALL_SOUND, delay = CRASH_FALL_SOUND_DELAY)
        target.soundSynth(CRASH_IMPACT_SOUND, delay = CRASH_IMPACT_SOUND_DELAY)
        val impact = CRASH_IMPACT.asRSCM(RSCMType.SPOTANIM)
        for (tile in YamaCrash.impacts(line)) {
            val coords = CoordGrid(centre.x + tile.dx, centre.z + tile.dz, centre.level)
            target.spotanimMapPrivate(impact, coords, height = 0, delay = tile.delay)
        }
        val shadows = YamaCrash.shadows(line)
        val shadow = CRASH_SHADOW.asRSCM(RSCMType.SPOTANIM)
        fight.scheduled +=
            ScheduledHazard(now + YamaCrash.SHADOW_AFTER_SET_TICKS, started = true) {
                if (target !in fight.players) return@ScheduledHazard
                for (tile in shadows) {
                    val coords = CoordGrid(centre.x + tile.dx, centre.z + tile.dz, centre.level)
                    val template = fight.template(coords)
                    if (!YamaArena.isWaveTile(template.x, template.z)) continue
                    target.spotanimMapPrivate(shadow, coords, height = 0, delay = tile.delay)
                }
                if (target.coords == centre) crashHit(fight, target, CRASH_MIN_HIT, CRASH_MAX_HIT)
            }
        fight.scheduled +=
            ScheduledHazard(now + YamaCrash.SHADOW_AFTER_SET_TICKS + 1, started = true) {
                val dx = target.coords.x - centre.x
                val dz = target.coords.z - centre.z
                if (shadows.any { it.dx == dx && it.dz == dz }) {
                    crashHit(fight, target, SHADOW_MIN_HIT, CRASH_MAX_HIT)
                }
            }
    }

    private fun crashHit(fight: YamaFight, player: Player, min: Int, max: Int) {
        val yama = fight.yama?.takeIf { it.isSlotAssigned } ?: return
        if (player !in fight.players) return
        val damage = deps.random.of(min, max)
        player.finishNpcHit(yama, 1, HitType.Typeless, damage, deps.playerHitModifier)
    }

    fun phaseEnded(fight: YamaFight) {
        fight.scheduled.removeAll { !it.started }
        for (player in fight.players) {
            player.burnTotal = 0
            player.burnSize = 0
        }
    }

    fun clear(fight: YamaFight) {
        fight.scheduled.clear()
        fight.streaks.clear()
        fight.waves.clear()
    }

    fun tick(fight: YamaFight, now: Int) {
        val due = fight.scheduled.filter { it.tick <= now }
        fight.scheduled.removeAll(due.toSet())
        due.forEach { it.action(now) }
        fight.waves.removeAll { now > it.start + WAVE_DURATION }
        for (player in fight.players) {
            val state = fight.state(player)
            val tile = fight.template(player.coords)
            val stationary = state.lastTile == player.coords
            strikeStreaks(fight, player, tile, stationary, now)
            processWaves(fight, player, state, now, tile)
            state.lastTile = player.coords
        }
        advanceStreaks(fight, now)
    }

    private fun hazardPhase(fight: YamaFight): Int =
        if (fight.contract?.hazardsAhead == true) {
            minOf(fight.phase + 1, SECOND_PHASE)
        } else {
            fight.phase
        }

    private fun startStreaks(fight: YamaFight, now: Int) {
        batch(fight, now)
        if (hazardPhase(fight) >= SECOND_PHASE) {
            fight.scheduled +=
                ScheduledHazard(now + SECOND_BATCH_DELAY, started = true) { batch(fight, it) }
        }
    }

    private fun batch(fight: YamaFight, now: Int) {
        val count = deps.random.of(MIN_STREAKS, MAX_STREAKS)
        val rows = YamaArena.STREAK_ROWS.toMutableList()
        repeat(count) {
            val row = rows.removeAt(deps.random.of(rows.size))
            val east = row.eastOnly || deps.random.of(2) == 0
            fight.streaks +=
                if (east) {
                    Streak(row.z, now, row.eastStart, 1, row.westStart - 1 - row.eastStart)
                } else {
                    Streak(row.z, now, row.westStart, -1, row.westStart - (row.eastStart + 1))
                }
        }
    }

    private fun startWaves(fight: YamaFight, now: Int) {
        val layer = WaveLayer.entries[deps.random.of(WaveLayer.entries.size)]
        launchWave(fight, now, layer)
        if (hazardPhase(fight) >= SECOND_PHASE) {
            fight.scheduled +=
                ScheduledHazard(now + SECOND_WAVE_DELAY, started = true) {
                    launchWave(fight, it, layer.other)
                }
        }
    }

    private fun launchWave(fight: YamaFight, now: Int, layer: WaveLayer) {
        val spotanim = SpotanimType(layer.spotanim.asRSCM(RSCMType.SPOTANIM))
        for ((x, z) in YamaArena.WAVE_TILES) {
            val coords = fight.instance(x, z)
            deps.worldRepo.spotanimMap(spotanim, coords, delay = layer.delay(x, z))
        }
        fight.waves += ActiveWave(now, layer)
    }

    private fun processWaves(
        fight: YamaFight,
        player: Player,
        state: YamaPlayerState,
        now: Int,
        tile: CoordGrid,
    ) {
        if (fight.inJudge || !YamaArena.isWaveTile(tile.x, tile.z)) return
        if (now - state.lastWaveHit < WAVE_HIT_GAP) return
        val arrived =
            fight.waves.any { YamaArena.waveArrival(it.start, it.layer, tile.x, tile.z) == now }
        if (!arrived) return
        state.lastWaveHit = now
        waveHit(fight, player, now)
    }

    private fun waveHit(fight: YamaFight, player: Player, now: Int) {
        val yama = fight.yama?.takeIf { it.isSlotAssigned } ?: return
        if (fight.isImmune(player, YamaElement.SHADOW, now)) {
            absorbHit(player, yama, YamaElement.SHADOW)
            return
        }
        val protectedBefore = overheadProtectionPrayerVarbits.any { player.vars[it] == 1 }
        val damage =
            if (fight.contract != null) {
                deps.random.of(CONTRACT_WAVE_MIN_HIT, CONTRACT_WAVE_MAX_HIT)
            } else {
                deps.random.of(WAVE_MIN_HIT, WAVE_MAX_HIT)
            }
        player.finishNpcHit(yama, 1, HitType.Typeless, damage, deps.playerHitModifier)
        player.lockOverheads(OVERHEAD_LOCK_TICKS)
        if (protectedBefore) player.mes(YamaText.INJURED)
    }

    private fun strikeStreaks(
        fight: YamaFight,
        player: Player,
        tile: CoordGrid,
        stationary: Boolean,
        now: Int,
    ) {
        if (!stationary || fight.inJudge) return
        for (streak in fight.streaks) {
            val elapsed = now - streak.start
            if (elapsed >= HIT_START && tile.z == streak.row) {
                strike(fight, player, streak, elapsed, tile)
            }
        }
    }

    private fun advanceStreaks(fight: YamaFight, now: Int) {
        val iterator = fight.streaks.iterator()
        while (iterator.hasNext()) {
            val streak = iterator.next()
            val elapsed = now - streak.start
            paint(fight, streak, elapsed)
            if (elapsed > HIT_START + streak.length / 2 + 1) iterator.remove()
        }
    }

    private fun paint(fight: YamaFight, streak: Streak, elapsed: Int) {
        val spotanim = SpotanimType(STREAK_SPOTANIM.asRSCM(RSCMType.SPOTANIM))
        fun at(offset: Int, delay: Int) {
            if (offset > streak.length) return
            val x = streak.x0 + streak.step * offset
            if (offset > 0 && !YamaArena.isWaveTile(x, streak.row)) return
            deps.worldRepo.spotanimMap(spotanim, fight.instance(x, streak.row), delay = delay)
        }
        when {
            elapsed < WINDUP -> at(0, SHORT_DELAY)
            elapsed == WINDUP -> at(1, LONG_DELAY)
            else -> {
                val step = elapsed - WINDUP
                at(2 * step, SHORT_DELAY)
                at(2 * step + 1, LONG_DELAY)
            }
        }
    }

    private fun strike(
        fight: YamaFight,
        player: Player,
        streak: Streak,
        elapsed: Int,
        tile: CoordGrid,
    ) {
        val covered = elapsed - HIT_START
        val offset = (tile.x - streak.x0) * streak.step
        if (offset < 1 || offset > streak.length || offset / 2 != covered) return
        val yama = fight.yama?.takeIf { it.isSlotAssigned } ?: return
        if (fight.isImmune(player, YamaElement.FIRE, deps.mapClock.cycle)) {
            absorbHit(player, yama, YamaElement.FIRE)
            return
        }
        val damage = deps.random.of(STREAK_MIN_HIT, STREAK_MAX_HIT)
        player.finishNpcHit(yama, 1, HitType.Typeless, damage, deps.playerHitModifier)
        player.anim(SEARED_SEQ)
        player.mes(SEARED_MESSAGE)
    }

    private fun absorbHit(player: Player, yama: Npc, element: YamaElement) {
        player.queueHit(
            yama,
            1,
            HitType.Typeless,
            0,
            deps.playerHitModifier,
            YamaStrikes.ABSORBED_HITMARK,
        )
        player.mes(element.absorbMessage(), ChatType.Spam)
    }

    private companion object {
        private const val ATTACK_INTERVAL = 8
        private const val STREAK_LEAD = 9
        private const val WAVE_LEAD = 10
        private const val SECOND_PHASE = 2
        private const val SECOND_BATCH_DELAY = 6
        private const val SECOND_WAVE_DELAY = 4
        private const val WAVE_DURATION = 26
        private const val MIN_STREAKS = 5
        private const val MAX_STREAKS = 10
        private const val WINDUP = 3
        private const val HIT_START = 4
        private const val SHORT_DELAY = 15
        private const val LONG_DELAY = 30
        private const val WAVE_MIN_HIT = 14
        private const val WAVE_MAX_HIT = 18
        private const val CONTRACT_WAVE_MIN_HIT = 24
        private const val CONTRACT_WAVE_MAX_HIT = 30
        private const val WAVE_HIT_GAP = 2
        private const val STREAK_MIN_HIT = 24
        private const val STREAK_MAX_HIT = 30
        private const val OVERHEAD_LOCK_TICKS = 3
        private const val STREAK_SPOTANIM = "spotanim.vfx_fire_wall_01"
        private const val CRASH_IMPACT = "spotanim.vfx_player_yama_falling_rock_impact01"
        private const val CRASH_SHADOW = "spotanim.vfx_flaming_skull_shadow_impact01"
        private const val CRASH_MIN_HIT = 11
        private const val SHADOW_MIN_HIT = 12
        private const val CRASH_MAX_HIT = 17
        private const val SEARED_SEQ = "seq.human_killerwatt_electricshock"
        private const val SEARED_MESSAGE = "<col=ff0000>You've been seared to the ground!</col>"
        private val HAZARD_ATTACK_INDEXES = intArrayOf(5, 9)
        private val CONTRACT_HAZARD_INDEXES = intArrayOf(4, 7)
        private const val CONTRACT_INTERVAL = 7
        private const val CONTRACT_STANDARDS = 2
        private const val CONTRACT_STREAK_LEAD = 8
        private const val CONTRACT_WAVE_LEAD = 9
        private const val CRASH_LAUNCH_SOUND = "synth.yama_crash_launch"
        private const val CRASH_FALL_SOUND = "synth.yama_crash_fall"
        private const val CRASH_IMPACT_SOUND = "synth.yama_crash_impact"
        private const val CRASH_LAUNCH_SOUND_DELAY = 3
        private const val CRASH_FALL_SOUND_DELAY = 100
        private const val CRASH_IMPACT_SOUND_DELAY = 105
    }
}
