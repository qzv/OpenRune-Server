package org.rsmod.api.stats.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.player.hook.PlayerHealCapHook
import org.rsmod.api.player.hook.limit
import org.rsmod.game.entity.Player

class HealCapLimitTest {
    private val player = Player()

    @Test
    fun `no hooks leave the maximum untouched`() {
        assertEquals(99, emptyList<PlayerHealCapHook>().limit(player, hitpoints = 50, maximum = 99))
    }

    @Test
    fun `a hook without a cap leaves the maximum untouched`() {
        val hooks = listOf(PlayerHealCapHook { null })
        assertEquals(99, hooks.limit(player, hitpoints = 50, maximum = 99))
    }

    @Test
    fun `healing is limited to the cap`() {
        val hooks = listOf(PlayerHealCapHook { 70 })
        assertEquals(70, hooks.limit(player, hitpoints = 50, maximum = 99))
    }

    @Test
    fun `the lowest cap wins`() {
        val hooks = listOf(PlayerHealCapHook { 70 }, PlayerHealCapHook { 40 })
        assertEquals(40, hooks.limit(player, hitpoints = 30, maximum = 99))
    }

    @Test
    fun `a player already above the cap is not limited by it`() {
        val hooks = listOf(PlayerHealCapHook { 40 })
        assertEquals(99, hooks.limit(player, hitpoints = 60, maximum = 99))
    }

    @Test
    fun `a cap above the maximum does not raise it`() {
        val hooks = listOf(PlayerHealCapHook { 120 })
        assertEquals(99, hooks.limit(player, hitpoints = 50, maximum = 99))
    }
}
