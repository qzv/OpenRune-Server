package org.rsmod.content.bosses.yama.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object YamaElementsTable {
    const val GLYPH_NAME = 0
    const val TEXT_COLOUR = 1
    const val GLYPH_LOC = 2
    const val DEACTIVATE_LOC = 3
    const val FLARE_MODELS = 4

    fun elements() =
        dbTable("dbtable.yama_elements", serverOnly = true) {
            column("glyph_name", GLYPH_NAME, VarType.STRING)
            column("text_colour", TEXT_COLOUR, VarType.STRING)
            column("glyph_loc", GLYPH_LOC, VarType.LOC)
            column("deactivate_loc", DEACTIVATE_LOC, VarType.LOC)
            column("flare_models", FLARE_MODELS, VarType.MODEL)
            row("dbrow.yama_element_fire") {
                column(GLYPH_NAME, "Glyph of Fire")
                column(TEXT_COLOUR, "<col=ff8e32>")
                columnRSCM(GLYPH_LOC, "loc.floorkit_summoning03_full02")
                columnRSCM(DEACTIVATE_LOC, "loc.floorkit_summoning03_full02_deactivate")
            }
            row("dbrow.yama_element_shadow") {
                column(GLYPH_NAME, "Glyph of Shadow")
                column(TEXT_COLOUR, "<col=a53fff>")
                columnRSCM(GLYPH_LOC, "loc.floorkit_summoning03_full01")
                columnRSCM(DEACTIVATE_LOC, "loc.floorkit_summoning03_full01_deactivate")
                column(FLARE_MODELS, 17003, 17006, 17009)
            }
        }
}
