package org.rsmod.content.minigames.colosseum.pack

import dev.openrune.definition.dbtables.DBTableBuilder
import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object ColosseumLootTables {
    const val TIER = 0
    const val TIER_OBJS = 1
    const val TIER_COUNTS = 2

    const val UNIQUE_OBJS = 0
    const val UNIQUE_WEIGHTS = 1
    const val UNIQUE_DEDUPE_OBJS = 2
    const val UNIQUE_BONUS_OBJ = 3
    const val UNIQUE_BONUS_CHANCE = 4
    const val UNIQUE_BONUS_MIN = 5
    const val UNIQUE_BONUS_MAX = 6

    private const val SPLINTERS = "obj.sunfiresplinter"
    private const val ONYX_BOLTS = "obj.xbows_crossbow_bolts_runite_tipped_onyx"
    private const val ECHO_CRYSTAL = "obj.echo_crystal"
    private val SUNFIRE_ARMOUR = arrayOf("obj.sunfire_helm", "obj.sunfire_body", "obj.sunfire_legs")
    private const val ECHO_BONUS_ONE_IN = 10
    private const val ECHO_BONUS_MIN = 2
    private const val ECHO_BONUS_MAX = 3

    private val TIERS =
        listOf(
            listOf(SPLINTERS to 80),
            listOf(
                "obj.rune_platebody" to 1,
                "obj.deathrune" to 150,
                "obj.chaosrune" to 150,
                SPLINTERS to 150,
                "obj.mcannonball" to 80,
                "obj.rune_kiteshield" to 4,
                "obj.rune_chainbody" to 1,
            ),
            listOf(
                ONYX_BOLTS to 30,
                "obj.snapdragon_seed" to 1,
                "obj.dragon_platelegs" to 1,
                SPLINTERS to 500,
                "obj.earth_orb" to 80,
                "obj.rune_2h_sword" to 2,
                "obj.gold_ore" to 150,
            ),
            listOf(
                "obj.dragon_bolts_unfeathered" to 200,
                "obj.ranarr_seed" to 4,
                SPLINTERS to 1100,
                "obj.dragon_arrowheads" to 150,
                "obj.adamantite_ore" to 100,
                "obj.deathrune" to 250,
            ),
            listOf(
                ONYX_BOLTS to 50,
                "obj.dragon_platelegs" to 2,
                SPLINTERS to 1750,
                "obj.rune_kiteshield" to 5,
                "obj.runite_ore" to 12,
                "obj.deathrune" to 300,
            ),
            listOf(
                ONYX_BOLTS to 75,
                SPLINTERS to 2500,
                "obj.dragon_platelegs" to 3,
                "obj.deathrune" to 300,
                "obj.rune_warhammer" to 5,
            ),
            listOf(
                ONYX_BOLTS to 100,
                "obj.rune_warhammer" to 8,
                "obj.dragon_platelegs" to 3,
                SPLINTERS to 3500,
                "obj.dragon_arrowheads" to 250,
            ),
            listOf(
                "obj.mcannonball" to 2000,
                "obj.dragon_plateskirt" to 5,
                "obj.dragon_arrowheads" to 350,
                ONYX_BOLTS to 150,
            ),
            listOf(
                "obj.uncut_onyx" to 1,
                "obj.dragon_plateskirt" to 5,
                "obj.rune_2h_sword" to 9,
                "obj.runite_ore" to 35,
            ),
        )

    private val EARLY_UNIQUES =
        listOf(
            ECHO_CRYSTAL to 4,
            SUNFIRE_ARMOUR[0] to 2,
            SUNFIRE_ARMOUR[1] to 2,
            SUNFIRE_ARMOUR[2] to 2,
        )

    private val LATE_UNIQUES =
        listOf(
            ECHO_CRYSTAL to 6,
            SUNFIRE_ARMOUR[0] to 3,
            SUNFIRE_ARMOUR[1] to 3,
            SUNFIRE_ARMOUR[2] to 3,
            "obj.tonalztics_of_ralos_uncharged" to 1,
        )

    fun tiers() =
        dbTable("dbtable.colosseum_loot_tiers", serverOnly = true) {
            column("tier", TIER, VarType.INT)
            column("objs", TIER_OBJS, VarType.OBJ)
            column("counts", TIER_COUNTS, VarType.INT)
            TIERS.forEachIndexed { tier, entries ->
                row("dbrow.colosseum_loot_tier_$tier") {
                    column(TIER, tier)
                    columnRSCM(TIER_OBJS, *entries.map { it.first }.toTypedArray())
                    column(TIER_COUNTS, *entries.map { it.second }.toTypedArray())
                }
            }
        }

    fun uniques() =
        dbTable("dbtable.colosseum_unique_tables", serverOnly = true) {
            column("objs", UNIQUE_OBJS, VarType.OBJ)
            column("weights", UNIQUE_WEIGHTS, VarType.INT)
            column("dedupe_objs", UNIQUE_DEDUPE_OBJS, VarType.OBJ)
            column("bonus_obj", UNIQUE_BONUS_OBJ, VarType.OBJ)
            column("bonus_chance", UNIQUE_BONUS_CHANCE, VarType.INT)
            column("bonus_min", UNIQUE_BONUS_MIN, VarType.INT)
            column("bonus_max", UNIQUE_BONUS_MAX, VarType.INT)
            uniqueRow("dbrow.colosseum_uniques_early", EARLY_UNIQUES)
            uniqueRow("dbrow.colosseum_uniques_late", LATE_UNIQUES)
        }

    private fun DBTableBuilder.uniqueRow(row: String, entries: List<Pair<String, Int>>) {
        row(row) {
            columnRSCM(UNIQUE_OBJS, *entries.map { it.first }.toTypedArray())
            column(UNIQUE_WEIGHTS, *entries.map { it.second }.toTypedArray())
            columnRSCM(UNIQUE_DEDUPE_OBJS, *SUNFIRE_ARMOUR)
            columnRSCM(UNIQUE_BONUS_OBJ, ECHO_CRYSTAL)
            column(UNIQUE_BONUS_CHANCE, ECHO_BONUS_ONE_IN)
            column(UNIQUE_BONUS_MIN, ECHO_BONUS_MIN)
            column(UNIQUE_BONUS_MAX, ECHO_BONUS_MAX)
        }
    }
}
