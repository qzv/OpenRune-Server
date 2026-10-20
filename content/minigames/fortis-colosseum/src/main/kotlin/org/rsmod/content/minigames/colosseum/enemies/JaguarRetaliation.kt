package org.rsmod.content.minigames.colosseum.enemies

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.npc.combatDefaultRetaliateOp
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.script.onNpcQueue
import org.rsmod.content.minigames.colosseum.ColosseumNpcs
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class JaguarRetaliation @Inject constructor(private val interactions: AiPlayerInteractions) :
    PluginScript() {
    override fun ScriptContext.startup() {
        val jaguar = ColosseumNpcs.JAGUAR.asRSCM(RSCMType.NPC)
        val type = checkNotNull(ServerCacheManager.getNpc(jaguar))
        onNpcQueue(type, "queue.com_retaliate_player") {
            npc.combatDefaultRetaliateOp(interactions)
        }
    }
}
