package org.rsmod.content.bosses.yama.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object YamaDefencesTable {
    const val STAB = 0
    const val SLASH = 1
    const val CRUSH = 2
    const val MAGIC = 3
    const val LIGHT = 4
    const val STANDARD = 5
    const val HEAVY = 6

    fun defences() =
        dbTable("dbtable.yama_defences", serverOnly = true) {
            column("stab", STAB, VarType.INT)
            column("slash", SLASH, VarType.INT)
            column("crush", CRUSH, VarType.INT)
            column("magic", MAGIC, VarType.INT)
            column("light", LIGHT, VarType.INT)
            column("standard", STANDARD, VarType.INT)
            column("heavy", HEAVY, VarType.INT)
            row("dbrow.yama_defences_final_phase") {
                column(STAB, 135)
                column(SLASH, 108)
                column(CRUSH, 449)
                column(MAGIC, 81)
                column(LIGHT, 449)
                column(STANDARD, 297)
                column(HEAVY, 449)
            }
            row("dbrow.yama_defences_melee_target") { column(MAGIC, -30) }
        }
}
