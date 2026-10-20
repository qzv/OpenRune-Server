package org.rsmod.api.npc.hit

import org.rsmod.game.entity.Npc

public fun interface NpcHitpointsRestoreHook {
    public fun onHitpointsRestored(npc: Npc)
}
