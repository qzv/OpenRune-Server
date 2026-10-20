package org.rsmod.content.bosses.yama

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.player.PlayerAttackRangeHook
import org.rsmod.game.entity.Player

internal class YamaRangeHook @Inject constructor(private val fights: YamaFights) :
    PlayerAttackRangeHook {
    override fun modify(player: Player, range: Int): Int {
        val fight = fights.of(player) ?: return range
        val halved = fight.contract?.halvesAttackRange == true
        if (!halved || fight.yama?.isSlotAssigned == false) return range
        return maxOf(MIN_RANGE, range / 2)
    }

    private companion object {
        private const val MIN_RANGE = 1
    }
}
