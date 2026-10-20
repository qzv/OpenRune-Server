package org.rsmod.game.entity.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.rsmod.map.CoordGrid

class EntityFaceAngleTest {
    @ParameterizedTest
    @CsvSource(
        "1820, 3103, 5, 1821, 3102, 1, 104",
        "1823, 3103, 5, 1828, 3104, 1, 1641",
        "1822, 3103, 1, 1823, 3104, 5, 1280",
        "1834, 3112, 1, 1830, 3114, 1, 663",
        "1824, 3101, 1, 1824, 3106, 1, 1024",
        "1824, 3108, 1, 1813, 3108, 1, 512",
        "1826, 3108, 1, 1827, 3108, 1, 1536",
        "1805, 9508, 1, 1805, 9507, 1, 0",
        "1830, 3106, 1, 1830, 3106, 1, 0",
    )
    fun `towards matches the recorded fallback angles`(
        fromX: Int,
        fromZ: Int,
        fromSize: Int,
        toX: Int,
        toZ: Int,
        toSize: Int,
        expected: Int,
    ) {
        val from = CoordGrid(fromX, fromZ, 0)
        val to = CoordGrid(toX, toZ, 0)
        assertEquals(expected, EntityFaceAngle.towards(from, fromSize, to, toSize))
    }
}
