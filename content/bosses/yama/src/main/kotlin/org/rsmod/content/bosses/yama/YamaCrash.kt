package org.rsmod.content.bosses.yama

import kotlin.math.abs

internal enum class CrashLine {
    VERTICAL,
    HORIZONTAL,
    DIAGONAL,
    ANTI_DIAGONAL,
}

internal data class CrashTile(val dx: Int, val dz: Int, val delay: Int)

internal object YamaCrash {
    const val START_AFTER_ATTACK_TICKS: Int = 2
    const val SETS: Int = 3
    const val SET_SPACING_TICKS: Int = 3
    const val SHADOW_AFTER_SET_TICKS: Int = 3
    const val SIDE_IMPACT_DELAY: Int = 3

    private const val CARDINAL_REACH: Int = 3
    private const val DIAGONAL_REACH: Int = 5
    private const val DIAGONAL_SIDE: Int = 2
    private const val SHADOW_FULL_DELAY: Int = 30
    private const val DIAGONAL_DELAY_STEP: Int = 6
    private val CARDINAL_DELAYS: IntArray = intArrayOf(30, 23, 15, 8)

    fun order(first: CrashLine, sets: Int = SETS): List<CrashLine> {
        require(first == CrashLine.VERTICAL || first == CrashLine.HORIZONTAL) {
            "a crash opens on a cardinal line"
        }
        val last = if (first == CrashLine.VERTICAL) CrashLine.HORIZONTAL else CrashLine.VERTICAL
        return listOf(first, CrashLine.DIAGONAL, last, CrashLine.ANTI_DIAGONAL, first).take(sets)
    }

    fun impacts(line: CrashLine): List<CrashTile> {
        val sides =
            when (line) {
                CrashLine.VERTICAL -> listOf(0 to -CARDINAL_REACH, 0 to CARDINAL_REACH)
                CrashLine.HORIZONTAL -> listOf(-CARDINAL_REACH to 0, CARDINAL_REACH to 0)
                CrashLine.DIAGONAL ->
                    listOf(-DIAGONAL_SIDE to DIAGONAL_SIDE, DIAGONAL_SIDE to -DIAGONAL_SIDE)
                CrashLine.ANTI_DIAGONAL ->
                    listOf(-DIAGONAL_SIDE to -DIAGONAL_SIDE, DIAGONAL_SIDE to DIAGONAL_SIDE)
            }
        val sideTiles = sides.map { (dx, dz) -> CrashTile(dx, dz, SIDE_IMPACT_DELAY) }
        return listOf(CrashTile(0, 0, 0)) + sideTiles
    }

    fun shadows(line: CrashLine): List<CrashTile> =
        when (line) {
            CrashLine.HORIZONTAL -> cardinalShadows(transpose = false)
            CrashLine.VERTICAL -> cardinalShadows(transpose = true)
            CrashLine.DIAGONAL -> diagonalShadows(anti = false)
            CrashLine.ANTI_DIAGONAL -> diagonalShadows(anti = true)
        }

    private fun cardinalShadows(transpose: Boolean): List<CrashTile> {
        val tiles = mutableListOf<CrashTile>()
        for (along in -CARDINAL_REACH..CARDINAL_REACH) {
            val delay = CARDINAL_DELAYS[abs(along)]
            for (across in -abs(along)..abs(along)) {
                tiles +=
                    if (transpose) {
                        CrashTile(across, along, delay)
                    } else {
                        CrashTile(along, across, delay)
                    }
            }
        }
        return tiles
    }

    private fun diagonalShadows(anti: Boolean): List<CrashTile> {
        val tiles = mutableListOf<CrashTile>()
        for (dx in -DIAGONAL_REACH..DIAGONAL_REACH) {
            for (dz in -DIAGONAL_REACH..DIAGONAL_REACH) {
                val distance = abs(dx) + abs(dz)
                val wrongQuadrant = if (anti) dx * dz < 0 else dx * dz > 0
                if (distance > DIAGONAL_REACH || wrongQuadrant) continue
                tiles += CrashTile(dx, dz, SHADOW_FULL_DELAY - DIAGONAL_DELAY_STEP * distance)
            }
        }
        return tiles
    }
}
