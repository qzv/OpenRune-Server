package org.rsmod.content.minigames.colosseum

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.player.PlayerAttackRangeHook
import org.rsmod.game.entity.Player

internal class MyopiaRangeHook @Inject constructor(private val modifiers: ColosseumModifiers) :
    PlayerAttackRangeHook {
    override fun modify(player: Player, range: Int): Int {
        if (player.runState == RunState.None) return range
        val level = modifiers.level(player, Modifier.Myopia)
        if (level == 0) return range
        return maxOf(MIN_RANGE, range - TILES_PER_LEVEL * level)
    }

    private companion object {
        private const val TILES_PER_LEVEL = 2
        private const val MIN_RANGE = 1
    }
}
