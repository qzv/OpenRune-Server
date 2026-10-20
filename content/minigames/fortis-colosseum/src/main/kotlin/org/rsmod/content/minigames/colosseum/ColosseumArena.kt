package org.rsmod.content.minigames.colosseum

import org.rsmod.api.random.GameRandom
import org.rsmod.api.table.ColosseumSpawnsRow
import org.rsmod.map.CoordGrid

internal object ColosseumArena {
    const val REGION = 7216

    val WALK_IN_END: CoordGrid by lazy { tile("dbrow.colosseum_walk_in_end") }
    val MINIMUS_START: CoordGrid by lazy { tile("dbrow.colosseum_minimus_start") }
    val CHEST: CoordGrid by lazy { tile("dbrow.colosseum_chest") }
    val CHEST_MINIMUS: CoordGrid by lazy { tile("dbrow.colosseum_chest_minimus") }
    val SEAT: CoordGrid by lazy { tile("dbrow.colosseum_seat") }
    val SOL_SPAWN: CoordGrid by lazy { tile("dbrow.colosseum_sol_spawn") }
    val SOL_PLAYER_START: CoordGrid by lazy { tile("dbrow.colosseum_sol_player_start") }
    val GRAVE: CoordGrid by lazy { tile("dbrow.colosseum_grave") }

    fun randomLobbyExit(random: GameRandom): CoordGrid {
        val exits = coords("dbrow.colosseum_lobby_exits")
        return exits[random.of(exits.size)]
    }

    fun logoutExits(): List<CoordGrid> = coords("dbrow.colosseum_logout_exits")

    fun warbandBox(): List<CoordGrid> = coords("dbrow.colosseum_warband_box")

    fun coords(row: String): List<CoordGrid> = ColosseumSpawnsRow.getRow(row).coords

    private fun tile(row: String): CoordGrid = coords(row).first()
}
