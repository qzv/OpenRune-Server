package org.rsmod.content.bosses.nex

import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceId
import org.rsmod.game.entity.Npc

@Singleton
internal class NexFights {
    private val fights = HashMap<InstanceId, NexFight>()

    fun of(instanceId: InstanceId): NexFight? = fights[instanceId]

    fun getOrCreate(instanceId: InstanceId): NexFight =
        fights.getOrPut(instanceId) { NexFight(instanceId) }

    fun end(instanceId: InstanceId) {
        fights.remove(instanceId)
    }

    fun fightFor(npc: Npc): NexFight? =
        fights.values.firstOrNull { fight -> fight.nex === npc || fight.mages.any { it === npc } }
}

internal class NexFight(val instanceId: InstanceId) {
    var spawnScheduled: Boolean = false
    var nex: Npc? = null
    val mages: MutableList<Npc> = ArrayList()
    var attackableTick: Int = Int.MAX_VALUE

    val inProgress: Boolean
        get() = nex != null
}
