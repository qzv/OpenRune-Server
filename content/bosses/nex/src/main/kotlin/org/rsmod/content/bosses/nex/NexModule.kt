package org.rsmod.content.bosses.nex

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.plugin.module.PluginModule

class NexModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(NexAttackHook::class.java)
    }
}
