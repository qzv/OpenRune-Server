package org.rsmod.api.combat.commons.player

import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

public fun interface PlayerAttackHook {
    public fun onAttack(player: Player, target: Npc, attack: CombatAttack.PlayerAttack)
}
