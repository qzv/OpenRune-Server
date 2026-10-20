package org.rsmod.content.bosses.nex

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.npc.hit.NpcDamageContributor
import org.rsmod.api.npc.hit.NpcHitHealHook
import org.rsmod.api.npc.hit.NpcHitpointsFloorHook
import org.rsmod.plugin.module.PluginModule

class NexModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(NexAttackHook::class.java)
        addSetBinding<NpcHitpointsFloorHook>(NexFloorHook::class.java)
        addSetBinding<NpcHitHealHook>(NexSiphonHook::class.java)
        addSetBinding<NpcDamageContributor>(NexContributionHook::class.java)
    }
}
