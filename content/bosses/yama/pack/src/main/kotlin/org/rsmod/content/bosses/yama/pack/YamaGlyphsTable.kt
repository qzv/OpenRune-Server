package org.rsmod.content.bosses.yama.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object YamaGlyphsTable {
    const val SLOT_DX = 0
    const val SLOT_DZ = 1
    const val SLOT_NEAR_THRONE = 2

    const val COST_MIN_DISTANCE = 0
    const val COST_FIRE_MIN = 1
    const val COST_FIRE_MAX = 2
    const val COST_SHADOW_MIN = 3
    const val COST_SHADOW_MAX = 4

    private class Slot(val key: String, val dx: Int, val dz: Int, val nearThrone: Boolean)

    private class Cost(val minDistance: Int, val fire: IntRange, val shadow: IntRange)

    private val SLOTS =
        listOf(
            Slot("north_west", -4, 8, nearThrone = true),
            Slot("south", 1, -4, nearThrone = true),
            Slot("north_east", 6, 8, nearThrone = true),
            Slot("west", -6, 1, nearThrone = false),
            Slot("east", 8, 1, nearThrone = false),
        )

    private val COSTS =
        listOf(
            Cost(5, 2..2, 1..2),
            Cost(4, 6..6, 3..5),
            Cost(3, 9..9, 5..8),
            Cost(0, 12..12, 9..12),
        )

    fun slots() =
        dbTable("dbtable.yama_glyph_slots", serverOnly = true) {
            column("dx", SLOT_DX, VarType.INT)
            column("dz", SLOT_DZ, VarType.INT)
            column("near_throne", SLOT_NEAR_THRONE, VarType.BOOLEAN)
            for (slot in SLOTS) {
                row("dbrow.yama_glyph_slot_${slot.key}") {
                    column(SLOT_DX, slot.dx)
                    column(SLOT_DZ, slot.dz)
                    column(SLOT_NEAR_THRONE, slot.nearThrone)
                }
            }
        }

    fun costs() =
        dbTable("dbtable.yama_glyph_costs", serverOnly = true) {
            column("min_distance", COST_MIN_DISTANCE, VarType.INT)
            column("fire_min", COST_FIRE_MIN, VarType.INT)
            column("fire_max", COST_FIRE_MAX, VarType.INT)
            column("shadow_min", COST_SHADOW_MIN, VarType.INT)
            column("shadow_max", COST_SHADOW_MAX, VarType.INT)
            for (cost in COSTS) {
                row("dbrow.yama_glyph_cost_${cost.minDistance}") {
                    column(COST_MIN_DISTANCE, cost.minDistance)
                    column(COST_FIRE_MIN, cost.fire.first)
                    column(COST_FIRE_MAX, cost.fire.last)
                    column(COST_SHADOW_MIN, cost.shadow.first)
                    column(COST_SHADOW_MAX, cost.shadow.last)
                }
            }
        }
}
