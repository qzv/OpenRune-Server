package org.rsmod.api.npc.hit

import jakarta.inject.Inject
import org.rsmod.api.npc.heal
import org.rsmod.game.entity.Npc

public class NpcHitpointsRestorer
@Inject
constructor(private val hooks: Set<@JvmSuppressWildcards NpcHitpointsRestoreHook>) {
    public fun heal(npc: Npc, amount: Int, showHitsplat: Boolean = false): Int {
        val healed = npc.heal(amount, showHitsplat)
        if (healed > 0) {
            restored(npc)
        }
        return healed
    }

    public fun restored(npc: Npc) {
        for (hook in hooks) {
            hook.onHitpointsRestored(npc)
        }
    }
}
