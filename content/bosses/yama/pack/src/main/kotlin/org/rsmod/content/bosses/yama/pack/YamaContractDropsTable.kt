package org.rsmod.content.bosses.yama.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object YamaContractDropsTable {
    const val OBJ = 0
    const val UNIQUE = 1

    private class Drop(val key: String, val obj: String, val unique: Boolean)

    private val DROPS =
        listOf(
            Drop("oathplate_helm", "obj.oathplate_helm", unique = true),
            Drop("oathplate_chest", "obj.oathplate_chest", unique = true),
            Drop("oathplate_legs", "obj.oathplate_legs", unique = true),
            Drop("soulflame_horn", "obj.soulflame_horn", unique = true),
            Drop("dossier", "obj.yama_dossier", unique = true),
            Drop("lockbox", "obj.forgotten_lockbox", unique = true),
            Drop("shards", "obj.oathplate_shards", unique = true),
            Drop("elite_clue", "obj.trail_elite_emote_exp1", unique = false),
            Drop("pet", "obj.yamapet", unique = false),
        )

    fun drops() =
        dbTable("dbtable.yama_contract_drops", serverOnly = true) {
            column("obj", OBJ, VarType.OBJ)
            column("unique", UNIQUE, VarType.BOOLEAN)
            for (drop in DROPS) {
                row("dbrow.yama_contract_drop_${drop.key}") {
                    columnRSCM(OBJ, drop.obj)
                    column(UNIQUE, drop.unique)
                }
            }
        }
}
