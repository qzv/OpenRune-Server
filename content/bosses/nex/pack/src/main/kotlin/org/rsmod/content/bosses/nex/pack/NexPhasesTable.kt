package org.rsmod.content.bosses.nex.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.columnCoord
import org.rsmod.map.CoordGrid

object NexPhasesTable {
    const val MAGE = 0
    const val MAGE_TILE = 1
    const val MAGE_FACING = 2
    const val SUMMON_LINE = 3
    const val SUMMON_SEQ = 4
    const val THRESHOLD = 5
    const val FAIL_LINE = 6
    const val PHASE_LINE = 7

    private const val NORTH_EAST = 2
    private const val SOUTH_WEST = 5
    private const val SOUTH_EAST = 7
    private const val NORTH_WEST = 0

    fun phases() =
        dbTable("dbtable.nex_phases", serverOnly = true) {
            column("mage", MAGE, VarType.NPC)
            column("mage_tile", MAGE_TILE, VarType.COORDGRID)
            column("mage_facing", MAGE_FACING, VarType.INT)
            column("summon_line", SUMMON_LINE, VarType.STRING)
            column("summon_seq", SUMMON_SEQ, VarType.SEQ)
            column("threshold", THRESHOLD, VarType.INT)
            column("fail_line", FAIL_LINE, VarType.STRING)
            column("phase_line", PHASE_LINE, VarType.STRING)

            row("dbrow.nex_phase_smoke") {
                columnRSCM(MAGE, "npc.nex_smokemage")
                columnCoord(MAGE_TILE, CoordGrid(2913, 5215, 0))
                column(MAGE_FACING, SOUTH_EAST)
                column(SUMMON_LINE, "Fumus!")
                columnRSCM(SUMMON_SEQ, "seq.nex_cast_attack")
                column(THRESHOLD, 80)
                column(FAIL_LINE, "Fumus, don't fail me!")
                column(PHASE_LINE, "Fill my soul with smoke!")
            }
            row("dbrow.nex_phase_shadow") {
                columnRSCM(MAGE, "npc.nex_shadowmage")
                columnCoord(MAGE_TILE, CoordGrid(2937, 5215, 0))
                column(MAGE_FACING, SOUTH_WEST)
                column(SUMMON_LINE, "Umbra!")
                columnRSCM(SUMMON_SEQ, "seq.nex_alternate_cast_attack")
                column(THRESHOLD, 60)
                column(FAIL_LINE, "Umbra, don't fail me!")
                column(PHASE_LINE, "Darken my shadow!")
            }
            row("dbrow.nex_phase_blood") {
                columnRSCM(MAGE, "npc.nex_bloodmage")
                columnCoord(MAGE_TILE, CoordGrid(2937, 5191, 0))
                column(MAGE_FACING, NORTH_WEST)
                column(SUMMON_LINE, "Cruor!")
                columnRSCM(SUMMON_SEQ, "seq.nex_cast_attack")
                column(THRESHOLD, 40)
                column(FAIL_LINE, "Cruor, don't fail me!")
                column(PHASE_LINE, "Flood my lungs with blood!")
            }
            row("dbrow.nex_phase_ice") {
                columnRSCM(MAGE, "npc.nex_icemage")
                columnCoord(MAGE_TILE, CoordGrid(2913, 5191, 0))
                column(MAGE_FACING, NORTH_EAST)
                column(SUMMON_LINE, "Glacies!")
                columnRSCM(SUMMON_SEQ, "seq.nex_alternate_cast_attack")
                column(THRESHOLD, 20)
                column(FAIL_LINE, "Glacies, don't fail me!")
                column(PHASE_LINE, "Infuse me with the power of ice!")
            }
        }
}
