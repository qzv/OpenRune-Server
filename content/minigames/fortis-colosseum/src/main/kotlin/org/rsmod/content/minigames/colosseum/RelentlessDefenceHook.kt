package org.rsmod.content.minigames.colosseum

import jakarta.inject.Inject
import org.rsmod.api.player.hook.PlayerDefenceLevelHook
import org.rsmod.game.entity.Player

internal class RelentlessDefenceHook
@Inject
constructor(private val modifiers: ColosseumModifiers) : PlayerDefenceLevelHook {
    override fun modify(player: Player, level: Int): Int {
        if (player.runState == RunState.None) return level
        val bypass =
            BYPASS_PERCENT[modifiers.level(player, Modifier.Relentless).coerceIn(0, MAX_LEVEL)]
        return level - level * bypass / PERCENT
    }

    private companion object {
        private val BYPASS_PERCENT = intArrayOf(0, 33, 66, 100)
        private const val MAX_LEVEL = 3
        private const val PERCENT = 100
    }
}
