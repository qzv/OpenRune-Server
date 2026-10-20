package org.rsmod.content.minigames.colosseum.pack

import dev.openrune.definition.dbtables.DBTableBuilder
import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import org.rsmod.map.CoordGrid

object ColosseumTables {
    const val WAVE = 0
    const val WARBAND = 1
    const val ENEMIES = 2
    const val REINFORCEMENTS = 3
    const val MAIN_TIER = 4
    const val RARE_TIER = 5
    const val UNIQUE_CHANCE = 6
    const val UNIQUE_TABLE = 7
    const val RARE_TIER_CHANCE = 8
    const val GUARANTEED = 9

    const val SPAWN_COORDS = 0

    private const val SHAMAN = "npc.colosseum_standard_mager"
    private const val JAVELIN = "npc.colosseum_javelin_colossus"
    private const val MANTICORE = "npc.colosseum_manticore"
    private const val SHOCKWAVE = "npc.colosseum_shockwave_colossus"
    private const val JAGUAR = "npc.colosseum_jaguar_warrior"
    private const val MINOTAUR = "npc.colosseum_minotaur"
    private const val SOL = "npc.colosseum_sol_p1"
    private const val QUIVER = "obj.dizanas_quiver_uncharged"
    private const val RARE_TIER_ONE_IN = 10

    private const val EARLY_UNIQUES = "dbrow.colosseum_uniques_early"
    private const val LATE_UNIQUES = "dbrow.colosseum_uniques_late"

    private class Wave(
        val enemies: List<String>,
        val reinforcements: List<String>,
        val mainTier: Int,
        val rareTier: Int? = null,
        val uniqueChance: Int = 0,
        val uniqueTable: String? = null,
        val warband: Boolean = true,
        val guaranteed: String? = null,
    )

    private val WAVES =
        listOf(
            Wave(listOf(SHAMAN), listOf(JAGUAR), mainTier = 0),
            Wave(listOf(SHAMAN, JAVELIN), listOf(JAGUAR), mainTier = 1),
            Wave(listOf(SHAMAN, JAVELIN, JAVELIN), listOf(JAGUAR), mainTier = 1, rareTier = 2),
            Wave(listOf(SHAMAN, MANTICORE), listOf(JAGUAR, SHAMAN), 1, 2, 124, EARLY_UNIQUES),
            Wave(
                listOf(SHAMAN, MANTICORE, JAVELIN),
                listOf(JAGUAR, SHAMAN),
                2,
                null,
                110,
                EARLY_UNIQUES,
            ),
            Wave(
                listOf(SHAMAN, MANTICORE, JAVELIN, JAVELIN),
                listOf(JAGUAR, SHAMAN),
                2,
                null,
                96,
                EARLY_UNIQUES,
            ),
            Wave(listOf(MANTICORE, JAVELIN, SHOCKWAVE), listOf(MINOTAUR), 2, 3, 82, LATE_UNIQUES),
            Wave(
                listOf(MANTICORE, JAVELIN, JAVELIN, SHOCKWAVE),
                listOf(MINOTAUR),
                2,
                4,
                68,
                LATE_UNIQUES,
            ),
            Wave(listOf(MANTICORE, MANTICORE, JAVELIN), listOf(MINOTAUR), 3, 5, 54, LATE_UNIQUES),
            Wave(
                listOf(MANTICORE, MANTICORE, JAVELIN, JAVELIN),
                listOf(MINOTAUR, SHAMAN),
                4,
                6,
                40,
                LATE_UNIQUES,
            ),
            Wave(
                listOf(MANTICORE, MANTICORE, JAVELIN, SHOCKWAVE),
                listOf(MINOTAUR, SHAMAN),
                5,
                7,
                26,
                LATE_UNIQUES,
            ),
            Wave(
                listOf(SOL),
                emptyList(),
                6,
                8,
                12,
                LATE_UNIQUES,
                warband = false,
                guaranteed = QUIVER,
            ),
        )

    private val SPAWN_POINTS =
        listOf(
            CoordGrid(1811, 3104),
            CoordGrid(1811, 3109),
            CoordGrid(1817, 3106),
            CoordGrid(1821, 3103),
            CoordGrid(1821, 3109),
            CoordGrid(1824, 3099),
            CoordGrid(1825, 3114),
            CoordGrid(1827, 3103),
            CoordGrid(1827, 3109),
            CoordGrid(1832, 3107),
            CoordGrid(1836, 3104),
            CoordGrid(1836, 3109),
        )

    private val SOLAR_FLARES =
        listOf(
            CoordGrid(1819, 3101),
            CoordGrid(1830, 3101),
            CoordGrid(1819, 3112),
            CoordGrid(1830, 3112),
        )

    private val REINFORCEMENT_GATES = listOf(CoordGrid(1823, 3092), CoordGrid(1823, 3120))

    private val SHAMAN_FALLBACK = listOf(CoordGrid(1824, 3104))

    private val LOBBY_EXITS =
        listOf(
            CoordGrid(1804, 9505),
            CoordGrid(1800, 9505),
            CoordGrid(1801, 9506),
            CoordGrid(1802, 9506),
            CoordGrid(1803, 9506),
            CoordGrid(1801, 9507),
            CoordGrid(1802, 9507),
            CoordGrid(1803, 9508),
        )

    private val LOGOUT_EXITS = listOf(CoordGrid(1809, 9506), CoordGrid(1809, 9507))

    private val SOL_QUARTET =
        listOf(
            CoordGrid(1824, 3110),
            CoordGrid(1821, 3107),
            CoordGrid(1827, 3106),
            CoordGrid(1826, 3105),
            CoordGrid(1822, 3105),
            CoordGrid(1827, 3110),
            CoordGrid(1826, 3108),
            CoordGrid(1823, 3105),
            CoordGrid(1821, 3106),
            CoordGrid(1822, 3108),
            CoordGrid(1826, 3110),
        )

    private val SOL_CRYSTAL_EDGES =
        listOf(
            CoordGrid(1819, 3113),
            CoordGrid(1831, 3101),
            CoordGrid(1819, 3100),
            CoordGrid(1818, 3101),
        )

    private val SOL_FLARE_TILES =
        listOf(
            CoordGrid(1826, 3108),
            CoordGrid(1826, 3105),
            CoordGrid(1823, 3108),
            CoordGrid(1823, 3105),
        )

    private val TILES =
        listOf(
            "walk_in_end" to listOf(CoordGrid(1824, 3101)),
            "minimus_start" to listOf(CoordGrid(1824, 3106)),
            "chest" to listOf(CoordGrid(1829, 3105)),
            "chest_minimus" to listOf(CoordGrid(1830, 3103)),
            "seat" to listOf(CoordGrid(1823, 3123)),
            "sol_spawn" to listOf(CoordGrid(1823, 3108)),
            "sol_player_start" to listOf(CoordGrid(1825, 3103)),
            "grave" to listOf(CoordGrid(1802, 9507)),
            "bees_sound_centre" to listOf(CoordGrid(1824, 3104)),
            "warband_box" to listOf(CoordGrid(1821, 3106), CoordGrid(1827, 3112)),
            "grave_fee_zone" to listOf(CoordGrid(1809, 3089), CoordGrid(1842, 3124, 1)),
            "sol_cutscene" to
                listOf(CoordGrid(1824, 3104), CoordGrid(1824, 3112), CoordGrid(1824, 3122)),
        )

    fun waves() =
        dbTable("dbtable.colosseum_waves", serverOnly = true) {
            column("wave", WAVE, VarType.INT)
            column("warband", WARBAND, VarType.BOOLEAN)
            column("enemies", ENEMIES, VarType.NPC)
            column("reinforcements", REINFORCEMENTS, VarType.NPC)
            column("main_tier", MAIN_TIER, VarType.INT)
            column("rare_tier", RARE_TIER, VarType.INT)
            column("unique_chance", UNIQUE_CHANCE, VarType.INT)
            column("unique_table", UNIQUE_TABLE, VarType.DBROW)
            column("rare_tier_chance", RARE_TIER_CHANCE, VarType.INT)
            column("guaranteed", GUARANTEED, VarType.OBJ)
            WAVES.forEachIndexed { index, wave ->
                row("dbrow.colosseum_wave_${index + 1}") {
                    column(WAVE, index + 1)
                    column(WARBAND, wave.warband)
                    columnRSCM(ENEMIES, *wave.enemies.toTypedArray())
                    if (wave.reinforcements.isNotEmpty()) {
                        columnRSCM(REINFORCEMENTS, *wave.reinforcements.toTypedArray())
                    }
                    column(MAIN_TIER, wave.mainTier)
                    wave.rareTier?.let {
                        column(RARE_TIER, it)
                        column(RARE_TIER_CHANCE, RARE_TIER_ONE_IN)
                    }
                    column(UNIQUE_CHANCE, wave.uniqueChance)
                    wave.uniqueTable?.let { columnRSCM(UNIQUE_TABLE, it) }
                    wave.guaranteed?.let { columnRSCM(GUARANTEED, it) }
                }
            }
        }

    fun spawns() =
        dbTable("dbtable.colosseum_spawns", serverOnly = true) {
            column("coords", SPAWN_COORDS, VarType.COORDGRID)
            spawnRow("dbrow.colosseum_spawn_points", SPAWN_POINTS)
            spawnRow("dbrow.colosseum_solar_flares", SOLAR_FLARES)
            spawnRow("dbrow.colosseum_reinforcement_gates", REINFORCEMENT_GATES)
            spawnRow("dbrow.colosseum_reinforcement_shaman_fallback", SHAMAN_FALLBACK)
            spawnRow("dbrow.colosseum_lobby_exits", LOBBY_EXITS)
            spawnRow("dbrow.colosseum_logout_exits", LOGOUT_EXITS)
            spawnRow("dbrow.colosseum_sol_quartet", SOL_QUARTET)
            spawnRow("dbrow.colosseum_sol_crystal_edges", SOL_CRYSTAL_EDGES)
            spawnRow("dbrow.colosseum_sol_flare_tiles", SOL_FLARE_TILES)
            for ((name, coords) in TILES) spawnRow("dbrow.colosseum_$name", coords)
        }

    private fun DBTableBuilder.spawnRow(row: String, coords: List<CoordGrid>) {
        row(row) { column(SPAWN_COORDS, *coords.map { it.packed }.toTypedArray()) }
    }
}
