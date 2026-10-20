package org.rsmod.content.minigames.colosseum.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object ColosseumWarbandSlots {
    const val NPC = 0
    const val OFFSET_X = 1
    const val OFFSET_Z = 2

    private val NORTH = 0 to 1
    private val SOUTH = 0 to -1
    private val WEST = -1 to 0
    private val EAST = 1 to 0

    private val SLOTS =
        mapOf(
            "melee" to Pair("npc.colosseum_warbander_melee_male", listOf(NORTH, SOUTH, WEST, EAST)),
            "ranged" to
                Pair("npc.colosseum_warbander_ranged_female", listOf(WEST, SOUTH, EAST, NORTH)),
            "mage" to Pair("npc.colosseum_warbander_mage_male", listOf(EAST, SOUTH, WEST, NORTH)),
        )

    fun slots() =
        dbTable("dbtable.colosseum_warband_slots", serverOnly = true) {
            column("npc", NPC, VarType.NPC)
            column("offset_x", OFFSET_X, VarType.INT)
            column("offset_z", OFFSET_Z, VarType.INT)
            for ((role, slot) in SLOTS) {
                row("dbrow.colosseum_warband_slot_$role") {
                    columnRSCM(NPC, slot.first)
                    column(OFFSET_X, *slot.second.map { it.first }.toTypedArray())
                    column(OFFSET_Z, *slot.second.map { it.second }.toTypedArray())
                }
            }
        }
}
