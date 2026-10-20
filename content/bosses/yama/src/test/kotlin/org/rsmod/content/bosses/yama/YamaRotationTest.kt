package org.rsmod.content.bosses.yama

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class YamaRotationTest {
    private fun encode(attack: YamaAttack): Char {
        val code = if (attack.element == YamaElement.FIRE) 'f' else 's'
        return if (attack.kind == YamaAttackKind.SPECIAL) code.uppercaseChar() else code
    }

    private fun sequence(
        opening: YamaElement,
        length: Int,
        standards: Int? = null,
        flareIndex: Int? = null,
    ): String =
        String(
            CharArray(length) {
                val attack =
                    when {
                        standards != null -> YamaRotation.attack(opening, it, standards)
                        flareIndex != null ->
                            YamaRotation.attack(opening, it, flareIndex = flareIndex)
                        else -> YamaRotation.attack(opening, it)
                    }
                encode(attack)
            }
        )

    private fun openingOf(expected: String): YamaElement =
        if (expected.first().lowercaseChar() == 'f') YamaElement.FIRE else YamaElement.SHADOW

    @ParameterizedTest
    @CsvSource(
        "fSsssFff",
        "sFfffSss",
        "fSsssFfffS",
        "fSsssFf",
        "sFfffSs",
        "sFff",
        "fSss",
        "fSsss",
        "sFfffSsss",
    )
    fun `produces the expected attack sequences`(expected: String) {
        assertEquals(expected, sequence(openingOf(expected), expected.length))
    }

    @Test
    fun `a special lands on every fourth attack after the opener`() {
        val specials =
            (0 until 14).filter {
                YamaRotation.attack(YamaElement.FIRE, it).kind == YamaAttackKind.SPECIAL
            }
        assertEquals(listOf(1, 5, 9, 13), specials)
    }

    @Test
    fun `contract specials land every third attack and alternate element`() {
        assertEquals("fSssFffSss", sequence(YamaElement.FIRE, 10, standards = 2))
    }

    @Test
    fun `standards after a flare cast keep the element of the standards before it`() {
        assertEquals("sFfffSfff", sequence(YamaElement.SHADOW, 9, flareIndex = 5))
    }

    @Test
    fun `the flare follow-up only covers the standards right after the cast`() {
        for (index in 9..12) {
            assertEquals(
                YamaRotation.attack(YamaElement.SHADOW, index),
                YamaRotation.attack(YamaElement.SHADOW, index, flareIndex = 5),
            )
        }
    }
}
