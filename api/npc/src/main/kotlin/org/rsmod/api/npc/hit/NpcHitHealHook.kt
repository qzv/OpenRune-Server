package org.rsmod.api.npc.hit

import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.Hit

public fun interface NpcHitHealHook {
    public fun heals(npc: Npc, hit: Hit): Boolean
}
