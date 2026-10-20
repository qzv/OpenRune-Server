package org.rsmod.content.minigames.colosseum.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object ColosseumEnemyTable {
    const val NPC = 0
    const val SPAWN_SEQ = 1
    const val ROUTEFINDS = 2
    const val GIB = 3
    const val GIB_SEQ = 4
    const val EXPLOSION = 5

    private const val HUMAN_STAFF_SPAWN = "seq.npc_human_staffready_spawn"
    private const val HUMAN_READY_SPAWN = "seq.npc_human_ready_spawn"
    private const val MINOTAUR_SPAWN = "seq.npc_minotaur_boss_spawn"

    private const val HUMAN_GIB = "npc.colosseum_human_gib"
    private const val HUMAN_GIB_SEQ = "seq.vfx_colosseum_human_gib_01"
    private const val HUMAN_EXPLOSION = "spotanim.vfx_colosseum_human_explosion_01"
    private const val COLOSSI_GIB = "npc.colosseum_colossi_gib"
    private const val COLOSSI_GIB_SEQ = "seq.vfx_colosseum_colossi_gib_01"
    private const val COLOSSI_EXPLOSION = "spotanim.vfx_colosseum_colossi_explosion_01"

    private class Enemy(
        val row: String,
        val npc: String,
        val gib: String = HUMAN_GIB,
        val gibSeq: String = HUMAN_GIB_SEQ,
        val explosion: String = HUMAN_EXPLOSION,
        val spawnSeq: String? = null,
        val routefinds: Boolean = false,
    )

    private val ENEMIES =
        listOf(
            Enemy(
                "melee",
                "npc.colosseum_warbander_melee_male",
                spawnSeq = HUMAN_STAFF_SPAWN,
                routefinds = true,
            ),
            Enemy(
                "ranged",
                "npc.colosseum_warbander_ranged_female",
                spawnSeq = HUMAN_STAFF_SPAWN,
                routefinds = true,
            ),
            Enemy(
                "mage",
                "npc.colosseum_warbander_mage_male",
                spawnSeq = HUMAN_STAFF_SPAWN,
                routefinds = true,
            ),
            Enemy("shaman", "npc.colosseum_standard_mager", spawnSeq = HUMAN_STAFF_SPAWN),
            Enemy("jaguar", "npc.colosseum_jaguar_warrior", spawnSeq = HUMAN_READY_SPAWN),
            Enemy(
                "javelin",
                "npc.colosseum_javelin_colossus",
                COLOSSI_GIB,
                COLOSSI_GIB_SEQ,
                COLOSSI_EXPLOSION,
            ),
            Enemy(
                "shockwave",
                "npc.colosseum_shockwave_colossus",
                COLOSSI_GIB,
                COLOSSI_GIB_SEQ,
                COLOSSI_EXPLOSION,
            ),
            Enemy(
                "manticore",
                "npc.colosseum_manticore",
                "npc.colosseum_manticore_gib",
                "seq.vfx_colosseum_manticore_gib_01",
                "spotanim.vfx_colosseum_manticore_explosion_01",
                spawnSeq = "seq.npc_manticore_01_spawn_02",
            ),
            Enemy(
                "minotaur",
                "npc.colosseum_minotaur",
                "npc.colosseum_minotaur_gib",
                "seq.vfx_colosseum_minotaur_gib_01",
                "spotanim.vfx_colosseum_minotaur_explosion_01",
                spawnSeq = MINOTAUR_SPAWN,
            ),
            Enemy(
                "minotaur_routefind",
                "npc.colosseum_minotaur_routefind",
                spawnSeq = MINOTAUR_SPAWN,
                routefinds = true,
            ),
            Enemy(
                "sol",
                "npc.colosseum_sol_p1",
                "npc.colosseum_sol_gib",
                "seq.vfx_colosseum_colossi_gib_01",
                "spotanim.vfx_colosseum_finalboss_explosion_01",
            ),
        )

    fun enemies() =
        dbTable("dbtable.colosseum_enemies", serverOnly = true) {
            column("npc", NPC, VarType.NPC)
            column("spawn_seq", SPAWN_SEQ, VarType.SEQ)
            column("routefinds", ROUTEFINDS, VarType.BOOLEAN)
            column("gib", GIB, VarType.NPC)
            column("gib_seq", GIB_SEQ, VarType.SEQ)
            column("explosion", EXPLOSION, VarType.GRAPHIC)
            for (enemy in ENEMIES) {
                row("dbrow.colosseum_enemy_${enemy.row}") {
                    columnRSCM(NPC, enemy.npc)
                    enemy.spawnSeq?.let { columnRSCM(SPAWN_SEQ, it) }
                    column(ROUTEFINDS, enemy.routefinds)
                    columnRSCM(GIB, enemy.gib)
                    columnRSCM(GIB_SEQ, enemy.gibSeq)
                    columnRSCM(EXPLOSION, enemy.explosion)
                }
            }
        }
}
