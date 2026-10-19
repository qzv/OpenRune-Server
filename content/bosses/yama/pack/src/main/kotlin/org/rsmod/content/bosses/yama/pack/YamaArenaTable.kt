package org.rsmod.content.bosses.yama.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object YamaArenaTable {
    const val Z = 0
    const val WAVE_RUNS = 1
    const val STREAK_EAST_START = 2
    const val STREAK_WEST_START = 3
    const val STREAK_EAST_ONLY = 4

    private class ArenaRow(
        val z: Int,
        val waveRuns: List<IntRange>,
        val streakEastStart: Int? = null,
        val streakWestStart: Int? = null,
        val streakEastOnly: Boolean = false,
    )

    private val ROWS =
        listOf(
            ArenaRow(10068, listOf(1500..1506), 1488, 1507, streakEastOnly = true),
            ArenaRow(10069, listOf(1493..1512), 1492, 1513),
            ArenaRow(10070, listOf(1490..1513), 1489, 1514),
            ArenaRow(10071, listOf(1490..1513), 1489, 1514),
            ArenaRow(10072, listOf(1490..1514), 1489, 1515),
            ArenaRow(10073, listOf(1491..1515), 1490, 1516),
            ArenaRow(10074, listOf(1489..1516), 1488, 1517),
            ArenaRow(10075, listOf(1490..1514), 1489, 1515),
            ArenaRow(10076, listOf(1490..1514), 1489, 1515),
            ArenaRow(10077, listOf(1490..1514), 1489, 1515),
            ArenaRow(10078, listOf(1491..1514), 1490, 1515),
            ArenaRow(10079, listOf(1491..1515), 1490, 1516),
            ArenaRow(10080, listOf(1491..1515), 1490, 1516),
            ArenaRow(10081, listOf(1490..1515), 1489, 1516),
            ArenaRow(10082, listOf(1490..1515), 1489, 1516),
            ArenaRow(10083, listOf(1490..1515), 1489, 1516),
            ArenaRow(10084, listOf(1491..1516), 1490, 1517),
            ArenaRow(10085, listOf(1490..1516), 1489, 1517),
            ArenaRow(10086, listOf(1492..1492, 1495..1515), 1491, 1516),
            ArenaRow(10087, listOf(1497..1510), 1496, 1511),
            ArenaRow(10088, listOf(1498..1507), 1497, 1519),
            ArenaRow(10090, listOf(1500..1507)),
            ArenaRow(10091, listOf(1499..1507)),
            ArenaRow(10092, listOf(1499..1507)),
            ArenaRow(10093, listOf(1499..1507)),
        )

    fun arena() =
        dbTable("dbtable.yama_arena", serverOnly = true) {
            column("z", Z, VarType.INT)
            column("wave_runs", WAVE_RUNS, VarType.INT, VarType.INT)
            column("streak_east_start", STREAK_EAST_START, VarType.INT)
            column("streak_west_start", STREAK_WEST_START, VarType.INT)
            column("streak_east_only", STREAK_EAST_ONLY, VarType.BOOLEAN)
            for (arenaRow in ROWS) {
                row("dbrow.yama_arena_${arenaRow.z}") {
                    column(Z, arenaRow.z)
                    val runs = arenaRow.waveRuns.flatMap { listOf(it.first, it.last) }
                    column(WAVE_RUNS, *runs.toTypedArray())
                    arenaRow.streakEastStart?.let { column(STREAK_EAST_START, it) }
                    arenaRow.streakWestStart?.let { column(STREAK_WEST_START, it) }
                    column(STREAK_EAST_ONLY, arenaRow.streakEastOnly)
                }
            }
        }
}
