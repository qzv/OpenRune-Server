package org.rsmod.content.minigames.colosseum.sol

import kotlin.math.abs
import kotlin.math.max

internal data class SolTile(val dx: Int, val dz: Int, val delay: Int)

internal enum class SolSide {
    North,
    East,
    South,
    West,
    NorthEast,
    SouthEast,
    SouthWest,
    NorthWest,
    Under;

    val diagonal: Boolean
        get() = this == NorthEast || this == SouthEast || this == SouthWest || this == NorthWest
}

internal object SolPatterns {
    const val SIZE = 5
    const val DELAY_PER_TILE = 2
    private const val CENTRE = SIZE / 2
    private const val DIAGONAL_LENGTH = 3
    private const val MAX_REACH = 8

    fun side(dx: Int, dz: Int): SolSide {
        val west = dx < 0
        val east = dx >= SIZE
        val south = dz < 0
        val north = dz >= SIZE
        return when {
            north && east -> SolSide.NorthEast
            south && east -> SolSide.SouthEast
            south && west -> SolSide.SouthWest
            north && west -> SolSide.NorthWest
            north -> SolSide.North
            east -> SolSide.East
            south -> SolSide.South
            west -> SolSide.West
            else -> SolSide.Under
        }
    }

    fun spear(variant: Int, side: SolSide, inArena: (Int, Int) -> Boolean): List<SolTile> {
        val tiles = footprint().toMutableSet()
        when {
            side == SolSide.Under -> Unit
            side.diagonal -> tiles += diagonalSpear(variant, side)
            else -> tiles += cardinalSpear(variant, side, inArena)
        }
        return tiles.filter { inArena(it.first, it.second) }.map { (x, z) -> tile(x, z) }
    }

    fun shield(variant: Int, inArena: (Int, Int) -> Boolean): List<SolTile> {
        val safeRing = variant + 1
        val tiles = mutableListOf<SolTile>()
        for (x in -MAX_REACH..SIZE - 1 + MAX_REACH) {
            for (z in -MAX_REACH..SIZE - 1 + MAX_REACH) {
                if (ring(x, z) == safeRing || !inArena(x, z)) continue
                tiles += tile(x, z)
            }
        }
        return tiles
    }

    fun ring(x: Int, z: Int): Int {
        val outX = if (x < 0) -x else if (x >= SIZE) x - (SIZE - 1) else 0
        val outZ = if (z < 0) -z else if (z >= SIZE) z - (SIZE - 1) else 0
        return max(outX, outZ)
    }

    private fun footprint(): List<Pair<Int, Int>> =
        (0 until SIZE).flatMap { x -> (0 until SIZE).map { z -> x to z } }

    private fun cardinalSpear(
        variant: Int,
        side: SolSide,
        inArena: (Int, Int) -> Boolean,
    ): List<Pair<Int, Int>> {
        val lines = if (variant == 1) listOf(1, 3) else listOf(0, 2, 4)
        val south = mutableListOf<Pair<Int, Int>>()
        for (across in 0 until SIZE) south += across to -1
        for (across in lines) {
            for (depth in 2..MAX_REACH) south += across to -depth
        }
        return south.map { (u, v) -> rotate(u, v, side) }.filter { inArena(it.first, it.second) }
    }

    private fun rotate(u: Int, v: Int, side: SolSide): Pair<Int, Int> =
        when (side) {
            SolSide.South -> u to v
            SolSide.North -> u to SIZE - 1 - v
            SolSide.West -> v to u
            SolSide.East -> SIZE - 1 - v to u
            else -> error("Not a cardinal side: $side")
        }

    private fun diagonalSpear(variant: Int, side: SolSide): List<Pair<Int, Int>> {
        val starts =
            if (variant == 1) listOf(SIZE - 1 to SIZE, SIZE to SIZE - 1)
            else listOf(CENTRE to SIZE, SIZE to CENTRE, SIZE to SIZE)
        val northEast =
            starts.flatMap { (x, z) ->
                (0 until DIAGONAL_LENGTH).map { step -> x + step to z + step }
            }
        val flipX = side == SolSide.SouthWest || side == SolSide.NorthWest
        val flipZ = side == SolSide.SouthWest || side == SolSide.SouthEast
        return northEast.map { (x, z) ->
            (if (flipX) SIZE - 1 - x else x) to (if (flipZ) SIZE - 1 - z else z)
        }
    }

    private fun tile(x: Int, z: Int): SolTile =
        SolTile(x, z, DELAY_PER_TILE * max(abs(x - CENTRE), abs(z - CENTRE)))
}
