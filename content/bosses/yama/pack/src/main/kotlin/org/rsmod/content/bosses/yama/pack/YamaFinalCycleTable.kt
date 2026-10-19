package org.rsmod.content.bosses.yama.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object YamaFinalCycleTable {
    const val INDEX = 0
    const val CRASH = 1
    const val STANDARD = 2
    const val WAVES = 3
    const val FLARE_PAIR = 4

    private class Plan(
        val crash: Boolean = false,
        val standard: Boolean = false,
        val waves: Boolean = false,
        val flarePair: Boolean = false,
    )

    private val CRASH_CAST = Plan(crash = true)
    private val CRASH_WAVES = Plan(crash = true, waves = true)
    private val STANDARD_ONLY = Plan(standard = true)
    private val STANDARD_WAVES = Plan(standard = true, waves = true)
    private val STANDARD_CRASH_WAVES = Plan(crash = true, standard = true, waves = true)
    private val STANDARD_FLARES = Plan(standard = true, flarePair = true)

    private val PLANS =
        listOf(
            CRASH_CAST,
            STANDARD_ONLY,
            STANDARD_ONLY,
            CRASH_WAVES,
            STANDARD_ONLY,
            STANDARD_ONLY,
            STANDARD_ONLY,
            CRASH_CAST,
            STANDARD_FLARES,
            STANDARD_ONLY,
            CRASH_CAST,
            STANDARD_WAVES,
            STANDARD_ONLY,
            STANDARD_CRASH_WAVES,
            STANDARD_WAVES,
            STANDARD_ONLY,
            STANDARD_ONLY,
            CRASH_CAST,
            STANDARD_FLARES,
            STANDARD_ONLY,
        )

    fun cycle() =
        dbTable("dbtable.yama_final_cycle", serverOnly = true) {
            column("index", INDEX, VarType.INT)
            column("crash", CRASH, VarType.BOOLEAN)
            column("standard", STANDARD, VarType.BOOLEAN)
            column("waves", WAVES, VarType.BOOLEAN)
            column("flare_pair", FLARE_PAIR, VarType.BOOLEAN)
            PLANS.forEachIndexed { index, plan ->
                row("dbrow.yama_final_cycle_$index") {
                    column(INDEX, index)
                    column(CRASH, plan.crash)
                    column(STANDARD, plan.standard)
                    column(WAVES, plan.waves)
                    column(FLARE_PAIR, plan.flarePair)
                }
            }
        }
}
