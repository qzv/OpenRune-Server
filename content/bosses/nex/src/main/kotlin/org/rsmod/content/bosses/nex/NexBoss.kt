package org.rsmod.content.bosses.nex

import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class NexBoss
@Inject
internal constructor(private val deps: BossDeps, private val smoke: NexSmoke) : PluginScript() {
    override fun ScriptContext.startup() {
        BossCombat.register(this, NexSpecs.spec, deps)
        deps.extensionRegistry.register(NexSpecs.STANDARD) { _, npc, target, _ ->
            smoke.standard(npc, target)
        }
        deps.extensionRegistry.register(NexSpecs.SPECIAL) { _, npc, target, _ ->
            smoke.special(npc, target)
        }
    }
}
