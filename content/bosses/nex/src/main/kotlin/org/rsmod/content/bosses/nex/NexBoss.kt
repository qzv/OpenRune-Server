package org.rsmod.content.bosses.nex

import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class NexBoss @Inject internal constructor(private val deps: BossDeps) : PluginScript() {
    override fun ScriptContext.startup() {
        BossCombat.register(this, NexSpecs.spec, deps)
    }
}
