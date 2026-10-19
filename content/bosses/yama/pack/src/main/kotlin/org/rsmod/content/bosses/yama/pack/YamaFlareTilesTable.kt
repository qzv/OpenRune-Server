package org.rsmod.content.bosses.yama.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object YamaFlareTilesTable {
    const val DX = 0
    const val DZ = 1
    const val PAIR = 2
    const val CYCLE_ORDER = 3

    private class Tile(val key: String, val dx: Int, val dz: Int, val pair: Int, val order: Int)

    private val TILES =
        listOf(
            Tile("south_west", -3, -3, pair = 1, order = 2),
            Tile("north_east", 9, 5, pair = 1, order = 1),
            Tile("north_west", -6, 5, pair = 2, order = 3),
            Tile("south_east", 6, -3, pair = 2, order = 0),
        )

    fun tiles() =
        dbTable("dbtable.yama_flare_tiles", serverOnly = true) {
            column("dx", DX, VarType.INT)
            column("dz", DZ, VarType.INT)
            column("pair", PAIR, VarType.INT)
            column("cycle_order", CYCLE_ORDER, VarType.INT)
            for (tile in TILES) {
                row("dbrow.yama_flare_tile_${tile.key}") {
                    column(DX, tile.dx)
                    column(DZ, tile.dz)
                    column(PAIR, tile.pair)
                    column(CYCLE_ORDER, tile.order)
                }
            }
        }
}
