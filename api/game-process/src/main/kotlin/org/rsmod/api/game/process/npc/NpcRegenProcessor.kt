package org.rsmod.api.game.process.npc

import jakarta.inject.Inject
import kotlin.math.sign
import org.rsmod.api.npc.hit.NpcHitpointsRestorer
import org.rsmod.game.entity.Npc

public class NpcRegenProcessor
@Inject
constructor(private val hitpointsRestorer: NpcHitpointsRestorer) {
    public fun process(npc: Npc) {
        npc.processStatsRegen()
    }

    private fun Npc.processStatsRegen() {
        if (regenRate == 0) {
            return
        }

        regenClock--
        if (regenClock > 0) {
            return
        }

        regenClock = regenRate
        regenStats()
    }

    private fun Npc.regenStats() {
        attackLvl = attackLvl.stepToward(baseAttackLvl)
        strengthLvl = strengthLvl.stepToward(baseStrengthLvl)
        defenceLvl = defenceLvl.stepToward(baseDefenceLvl)
        rangedLvl = rangedLvl.stepToward(baseRangedLvl)
        magicLvl = magicLvl.stepToward(baseMagicLvl)
        val previousHitpoints = hitpoints
        hitpoints = hitpoints.stepToward(baseHitpointsLvl)
        if (hitpoints > previousHitpoints) {
            hitpointsRestorer.restored(this)
        }
    }

    private fun Int.stepToward(target: Int) = this + (target - this).sign
}
