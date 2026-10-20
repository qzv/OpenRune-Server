package org.rsmod.content.bosses.nex

import jakarta.inject.Inject
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

internal class NexAttackHook
@Inject
constructor(private val fights: NexFights, private val mapClock: MapClock) :
    NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        val fight = fights.fightFor(npc) ?: return NpcAttackValidateResult.Pass
        if (npc !== fight.nex) {
            if (fight.mageReleased(npc)) return NpcAttackValidateResult.Pass
            val name = npc.visType.name
            return NpcAttackValidateResult.Deny("$name is currently immune to your attacks.")
        }
        if (mapClock.cycle < fight.attackableTick) {
            return NpcAttackValidateResult.Deny(NEX_IMMUNE)
        }
        return NpcAttackValidateResult.Pass
    }

    private companion object {
        private const val NEX_IMMUNE = "Nex is immune to your attacks!"
    }
}
