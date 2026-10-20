package org.rsmod.content.bosses.yama

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.table.YamaFinalCycleRow
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

internal object YamaFinalCycle {
    private const val PERIOD = 10
    private const val FIRST_REPEAT = 10

    private val PLANS: List<YamaFinalCycleRow> by lazy {
        YamaFinalCycleRow.all().sortedBy { it.index }
    }

    fun plan(index: Int): YamaFinalCycleRow {
        val folded =
            if (index < PLANS.size) index else FIRST_REPEAT + (index - FIRST_REPEAT) % PERIOD
        return PLANS[folded]
    }
}

@Singleton
internal class YamaPhaseThree
@Inject
constructor(
    private val deps: BossDeps,
    private val fights: YamaFights,
    private val hazards: YamaHazards,
    private val flares: YamaVoidFlares,
    private val strikes: YamaStrikes,
) {
    fun attack(yama: Npc, player: Player, index: Int) {
        val fight = fights.of(player) ?: return
        val plan = YamaFinalCycle.plan(index)
        val melee = strikes.canMelee(yama, player)
        val acquisition = fight.contract?.acquisition == true
        if (plan.waves) hazards.launchWavePair(fight, deps.mapClock.cycle)
        if (acquisition && index == ACQUISITION_BURST_INDEX) {
            flares.spawnBurst(fight)
        } else if (plan.flarePair && !acquisition) {
            flares.spawnPair(fight)
        }
        val sets = if (acquisition) ACQUISITION_CRASH_SETS else YamaCrash.SETS
        when {
            melee -> {
                strikes.melee(yama, player)
                flares.spawnMelee(fight)
            }
            plan.standard -> standard(fight, yama, player)
            else -> {
                yama.anim(YamaStrikes.STANDARD_SEQ)
                hazards.crash(fight, deps.mapClock.cycle, sets)
            }
        }
        if (plan.standard && plan.crash && !melee) hazards.crash(fight, deps.mapClock.cycle, sets)
    }

    private companion object {
        private const val ACQUISITION_BURST_INDEX = 3
        private const val ACQUISITION_CRASH_SETS = 5
    }

    private fun standard(fight: YamaFight, yama: Npc, player: Player) {
        val first = fight.layout.opening.opposite
        val element = if (fight.finalStandards++ % 2 == 0) first else first.opposite
        strikes.standard(yama, player, element)
    }
}
