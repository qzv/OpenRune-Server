package org.rsmod.api.bossbar.plugin

import jakarta.inject.Inject
import jakarta.inject.Provider
import org.rsmod.api.npc.hit.NpcHitpointsRestoreHook
import org.rsmod.game.entity.Npc

public class BossHpBarRestoreHook
@Inject
constructor(
    private val contributor: BossHpBarDamageContributor,
    private val scriptProvider: Provider<BossHpBarScript>,
) : NpcHitpointsRestoreHook {
    override fun onHitpointsRestored(npc: Npc) {
        if (contributor.isClosingAfterDeath(npc)) return
        val script = scriptProvider.get()
        for (player in contributor.barPlayersOf(npc)) script.onUpdate(player, npc)
    }
}
