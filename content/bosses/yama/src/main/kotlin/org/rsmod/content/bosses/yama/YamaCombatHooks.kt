package org.rsmod.content.bosses.yama

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.player.PlayerAttackHook
import org.rsmod.api.player.hook.PlayerRunValidateHook
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

internal class YamaJudgeAttackHook @Inject constructor(private val judge: YamaJudge) :
    PlayerAttackHook {
    override fun onAttack(player: Player, target: Npc, attack: CombatAttack.PlayerAttack) {
        judge.onPlayerAttack(target, attack)
    }
}

internal class YamaRunHook
@Inject
constructor(private val fights: YamaFights, private val mapClock: MapClock) :
    PlayerRunValidateHook {
    override fun validate(player: Player): String? {
        val fight = fights.of(player) ?: return null
        if (mapClock.cycle >= fight.state(player).noRunUntil) return null
        return RUN_BLOCKED
    }

    private companion object {
        private const val RUN_BLOCKED =
            "<col=ff3045>Yama's intimidating presence prevents you from running!"
    }
}
