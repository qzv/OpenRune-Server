package org.rsmod.content.other.consumables

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.player.hook.PlayerHealCapHook
import org.rsmod.game.entity.Player

class HitpointsHealingTest {
    private val player = Player()

    @Test
    fun `saradomin brew boosts above base without a cap`() {
        val target = boostedHitpoints(base = 99, current = 99, constant = 2, percent = 15)
        assertEquals(115, target)
        assertEquals(16, NO_CAPS.healAmount(player, hitpoints = 99, target = target))
    }

    @Test
    fun `a brew boost above base is limited to the cap`() {
        val target = boostedHitpoints(base = 99, current = 40, constant = 2, percent = 15)
        assertEquals(56, target)

        val caps = listOf(PlayerHealCapHook { 50 })
        assertEquals(10, caps.healAmount(player, hitpoints = 40, target = target))
    }

    @Test
    fun `a boost never exceeds base plus the boost amount`() {
        val target = boostedHitpoints(base = 99, current = 110, constant = 2, percent = 15)
        assertEquals(115, target)
        assertEquals(5, NO_CAPS.healAmount(player, hitpoints = 110, target = target))
    }

    @Test
    fun `a player already above the cap heals normally`() {
        val target = boostedHitpoints(base = 99, current = 60, constant = 2, percent = 15)
        val caps = listOf(PlayerHealCapHook { 50 })
        assertEquals(16, caps.healAmount(player, hitpoints = 60, target = target))
    }

    @Test
    fun `the lowest of several caps wins`() {
        val caps =
            listOf(PlayerHealCapHook { 70 }, PlayerHealCapHook { null }, PlayerHealCapHook { 45 })
        assertEquals(5, caps.healAmount(player, hitpoints = 40, target = 99))
    }

    @Test
    fun `a cap never reduces hitpoints`() {
        val caps = listOf(PlayerHealCapHook { 30 })
        assertEquals(0, caps.healAmount(player, hitpoints = 30, target = 99))
        assertEquals(0, NO_CAPS.healAmount(player, hitpoints = 80, target = 70))
    }

    @Test
    fun `restores stop at base and skip players at or above it`() {
        assertEquals(99, restoredHitpoints(base = 99, current = 95, constant = 10, percent = 0))
        assertEquals(60, restoredHitpoints(base = 99, current = 50, constant = 10, percent = 0))
        assertEquals(105, restoredHitpoints(base = 99, current = 105, constant = 10, percent = 0))
    }

    @Test
    fun `a restore is limited to the cap`() {
        val target = restoredHitpoints(base = 99, current = 20, constant = 20, percent = 0)
        val caps = listOf(PlayerHealCapHook { 30 })
        assertEquals(10, caps.healAmount(player, hitpoints = 20, target = target))
    }

    private companion object {
        val NO_CAPS: List<PlayerHealCapHook> = emptyList()
    }
}
