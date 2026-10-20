package org.rsmod.content.bosses.yama

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.hit.HitType

class YamaJudgePrayersTest {
    @Test
    fun `judge starts protecting melee and missiles`() {
        val melee = YamaJudgePrayers.resolve(false, HitType.Melee, 100)
        assertEquals(44, melee.damage)
        assertFalse(melee.protectsMagic)
        assertEquals(44, YamaJudgePrayers.resolve(false, HitType.Ranged, 100).damage)
    }

    @Test
    fun `an unprotected magic hit lands in full and switches the prayer`() {
        val magic = YamaJudgePrayers.resolve(false, HitType.Magic, 100)
        assertEquals(100, magic.damage)
        assertTrue(magic.protectsMagic)
    }

    @Test
    fun `an unprotected melee hit lands in full and switches back`() {
        val melee = YamaJudgePrayers.resolve(true, HitType.Melee, 100)
        assertEquals(100, melee.damage)
        assertFalse(melee.protectsMagic)
    }

    @Test
    fun `ranged is always protected and typeless hits are untouched`() {
        assertEquals(44, YamaJudgePrayers.resolve(true, HitType.Ranged, 100).damage)
        val typeless = YamaJudgePrayers.resolve(true, HitType.Typeless, 100)
        assertEquals(100, typeless.damage)
        assertTrue(typeless.protectsMagic)
    }
}
