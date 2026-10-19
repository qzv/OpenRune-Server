package org.rsmod.api.combat.formulas

import java.util.EnumSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.rsmod.api.combat.formulas.accuracy.magic.MagicAccuracyOperations
import org.rsmod.api.combat.formulas.accuracy.melee.MeleeAccuracyOperations
import org.rsmod.api.combat.formulas.accuracy.ranged.RangedAccuracyOperations
import org.rsmod.api.combat.formulas.attributes.CombatMeleeAttributes
import org.rsmod.api.combat.formulas.attributes.CombatNpcAttributes
import org.rsmod.api.combat.formulas.attributes.CombatRangedAttributes
import org.rsmod.api.combat.formulas.attributes.CombatSpellAttributes
import org.rsmod.api.combat.formulas.maxhit.magic.MagicMaxHitOperations
import org.rsmod.api.combat.formulas.maxhit.melee.MeleeMaxHitOperations
import org.rsmod.api.combat.formulas.maxhit.ranged.RangedMaxHitOperations

private const val BASE = 10_000

private val DEMON: EnumSet<CombatNpcAttributes> = EnumSet.of(CombatNpcAttributes.Demon)

private val NOT_DEMON: EnumSet<CombatNpcAttributes> =
    EnumSet.noneOf(CombatNpcAttributes::class.java)

private fun melee(vararg attrs: CombatMeleeAttributes): EnumSet<CombatMeleeAttributes> =
    EnumSet.noneOf(CombatMeleeAttributes::class.java).apply { addAll(attrs) }

private fun ranged(vararg attrs: CombatRangedAttributes): EnumSet<CombatRangedAttributes> =
    EnumSet.noneOf(CombatRangedAttributes::class.java).apply { addAll(attrs) }

private fun spell(vararg attrs: CombatSpellAttributes): EnumSet<CombatSpellAttributes> =
    EnumSet.noneOf(CombatSpellAttributes::class.java).apply { addAll(attrs) }

internal class DemonbaneEffectivenessTest {
    @ParameterizedTest
    @CsvSource("100,70", "70,49", "120,84", "150,105", "200,140")
    fun `the seventy percent weapon bonus scales with the npc percent`(percent: Int, bonus: Int) {
        assertEquals(bonus, DemonbaneEffectiveness.scaleBonus(70, percent))
    }

    @Nested
    inner class Arclight {
        private val attrs = melee(CombatMeleeAttributes.Arclight)

        @ParameterizedTest
        @CsvSource("100,17000", "70,14900", "120,18400", "150,20500", "200,24000")
        fun `accuracy`(percent: Int, expected: Int) {
            assertEquals(
                expected,
                MeleeAccuracyOperations.modifyAttackRoll(BASE, attrs, DEMON, percent),
            )
        }

        @ParameterizedTest
        @CsvSource("100,17000", "70,14900", "120,18400", "150,20500", "200,24000")
        fun `damage`(percent: Int, expected: Int) {
            assertEquals(
                expected,
                MeleeMaxHitOperations.modifyBaseDamage(BASE, attrs, DEMON, percent),
            )
        }

        @ParameterizedTest
        @ValueSource(ints = [70, 100, 200])
        fun `non demons get no bonus`(percent: Int) {
            assertEquals(
                BASE,
                MeleeMaxHitOperations.modifyBaseDamage(BASE, attrs, NOT_DEMON, percent),
            )
            assertEquals(
                BASE,
                MeleeAccuracyOperations.modifyAttackRoll(BASE, attrs, NOT_DEMON, percent),
            )
        }
    }

    @Nested
    inner class BurningClaws {
        private val attrs = melee(CombatMeleeAttributes.BurningClaws)

        @ParameterizedTest
        @CsvSource("100,10500", "70,10350", "120,10600", "150,10750", "200,11000")
        fun `accuracy`(percent: Int, expected: Int) {
            assertEquals(
                expected,
                MeleeAccuracyOperations.modifyAttackRoll(BASE, attrs, DEMON, percent),
            )
        }

        @ParameterizedTest
        @CsvSource("100,10500", "70,10350", "120,10600", "150,10750", "200,11000")
        fun `damage`(percent: Int, expected: Int) {
            assertEquals(
                expected,
                MeleeMaxHitOperations.modifyBaseDamage(BASE, attrs, DEMON, percent),
            )
        }
    }

    @Nested
    inner class Silverlight {
        private val attrs = melee(CombatMeleeAttributes.Silverlight)

        @ParameterizedTest
        @CsvSource("100,16000", "70,14200", "120,17200", "150,19000", "200,22000")
        fun `damage`(percent: Int, expected: Int) {
            assertEquals(
                expected,
                MeleeMaxHitOperations.modifyBaseDamage(BASE, attrs, DEMON, percent),
            )
        }
    }

    @Nested
    inner class ScorchingBow {
        private val attrs = ranged(CombatRangedAttributes.ScorchingBow)

        @ParameterizedTest
        @CsvSource("100,13000", "70,12100", "120,13600", "150,14500", "200,16000")
        fun `accuracy`(percent: Int, expected: Int) {
            val roll = RangedAccuracyOperations.modifyAttackRoll(BASE, 0, 0, attrs, DEMON, percent)
            assertEquals(expected, roll)
        }

        @ParameterizedTest
        @CsvSource("100,13000", "70,12100", "120,13600", "150,14500", "200,16000")
        fun `damage`(percent: Int, expected: Int) {
            val damage = RangedMaxHitOperations.modifyBaseDamage(BASE, 0, attrs, DEMON, percent)
            assertEquals(expected, damage)
        }
    }

    @Nested
    inner class DemonbaneSpellAccuracy {
        private fun roll(spell: EnumSet<CombatSpellAttributes>, percent: Int): Int =
            MagicAccuracyOperations.modifySpellAttackRoll(BASE, 0, spell, DEMON, percent)

        @ParameterizedTest
        @CsvSource("100,12000", "70,11400", "120,12400", "150,13000", "200,14000")
        fun `unmarked`(percent: Int, expected: Int) {
            assertEquals(expected, roll(spell(CombatSpellAttributes.Demonbane), percent))
        }

        @ParameterizedTest
        @CsvSource("100,14000", "70,12800", "120,14800", "150,16000", "200,18000")
        fun `marked`(percent: Int, expected: Int) {
            val marked =
                spell(CombatSpellAttributes.Demonbane, CombatSpellAttributes.MarkOfDarkness)
            assertEquals(expected, roll(marked, percent))
        }

        @ParameterizedTest
        @CsvSource("100,14000", "70,12800", "120,14800", "150,16000", "200,18000")
        fun `purging staff without the mark`(percent: Int, expected: Int) {
            val staff = spell(CombatSpellAttributes.Demonbane, CombatSpellAttributes.PurgingStaff)
            assertEquals(expected, roll(staff, percent))
        }

        @ParameterizedTest
        @CsvSource("100,18000", "70,15600", "120,19600", "150,22000", "200,26000")
        fun `purging staff with the mark`(percent: Int, expected: Int) {
            val staff =
                spell(
                    CombatSpellAttributes.Demonbane,
                    CombatSpellAttributes.MarkOfDarkness,
                    CombatSpellAttributes.PurgingStaff,
                )
            assertEquals(expected, roll(staff, percent))
        }
    }

    @Nested
    inner class DemonbaneSpellDamage {
        private fun range(
            spell: EnumSet<CombatSpellAttributes>,
            percent: Int,
            modifiedDamage: Int = 100,
        ): IntRange =
            MagicMaxHitOperations.modifySpellDamageRange(
                modifiedDamage = modifiedDamage,
                baseDamage = modifiedDamage,
                attackRate = 5,
                targetWeaknessPercent = 0,
                spellAttributes = spell,
                npcAttributes = DEMON,
                demonbanePercent = percent,
            )

        private val marked =
            spell(CombatSpellAttributes.Demonbane, CombatSpellAttributes.MarkOfDarkness)

        private val markedStaff =
            spell(
                CombatSpellAttributes.Demonbane,
                CombatSpellAttributes.MarkOfDarkness,
                CombatSpellAttributes.PurgingStaff,
            )

        @ParameterizedTest
        @CsvSource("100,125", "70,125", "120,130", "150,137", "200,150")
        fun `marked`(percent: Int, expectedMax: Int) {
            assertEquals(0..expectedMax, range(marked, percent))
        }

        @ParameterizedTest
        @CsvSource("100,150", "70,150", "120,160", "150,175", "200,200")
        fun `marked with the purging staff`(percent: Int, expectedMax: Int) {
            assertEquals(0..expectedMax, range(markedStaff, percent))
        }

        @ParameterizedTest
        @CsvSource("100,54", "70,54", "120,57", "150,63", "200,72")
        fun `the scaled mark bonus rounds the max hit down`(percent: Int, expectedMax: Int) {
            assertEquals(0..expectedMax, range(markedStaff, percent, modifiedDamage = 36))
        }

        @ParameterizedTest
        @ValueSource(ints = [70, 100, 120, 200])
        fun `unmarked demonbane adds no damage`(percent: Int) {
            assertEquals(0..100, range(spell(CombatSpellAttributes.Demonbane), percent))
        }

        @Test
        fun `a raised percent without the demon attribute adds no damage`() {
            val range =
                MagicMaxHitOperations.modifySpellDamageRange(
                    modifiedDamage = 100,
                    baseDamage = 100,
                    attackRate = 5,
                    targetWeaknessPercent = 0,
                    spellAttributes = markedStaff,
                    npcAttributes = NOT_DEMON,
                    demonbanePercent = 200,
                )
            assertEquals(0..100, range)
        }
    }
}
