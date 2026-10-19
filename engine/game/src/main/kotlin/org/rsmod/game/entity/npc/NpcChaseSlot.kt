package org.rsmod.game.entity.npc

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntityAvatar
import org.rsmod.map.CoordGrid

public interface NpcChaseSlot {
    public fun slot(npc: Npc, target: PathingEntityAvatar): CoordGrid?

    public fun holds(npc: Npc, target: PathingEntityAvatar): Boolean = false
}
