package org.rsmod.content.bosses.nex

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.table.NexPhasesRow
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid

@Singleton
internal class NexFights
@Inject
constructor(private val manager: InstanceManager, private val playerList: PlayerList) {
    private val fights = HashMap<InstanceId, NexFight>()

    fun of(instanceId: InstanceId): NexFight? = fights[instanceId]

    fun getOrCreate(instanceId: InstanceId): NexFight =
        fights.getOrPut(instanceId) { NexFight(instanceId) }

    fun end(instanceId: InstanceId) {
        fights.remove(instanceId)
    }

    fun attach(fight: NexFight, npc: Npc) {
        manager.attachNpc(fight.instanceId, npc)
    }

    fun fightFor(npc: Npc): NexFight? =
        fights.values.firstOrNull { fight -> fight.nex === npc || fight.mages.any { it === npc } }

    fun players(fight: NexFight): List<Player> {
        val session = manager.sessionForId(fight.instanceId) ?: return emptyList()
        return playerList.filter { manager.sessionForPlayer(it) === session && it.isValidTarget() }
    }

    fun shout(fight: NexFight, npc: Npc, text: String) {
        npc.say(text)
        val line = "${npc.visType.name}|$text"
        for (player in players(fight)) player.mes(line, ChatType.NpcSay)
    }
}

internal class NexFight(val instanceId: InstanceId) {
    var spawnScheduled: Boolean = false
    var nex: Npc? = null
    var home: CoordGrid? = null
    val mages: MutableList<Npc> = ArrayList()
    var attackableTick: Int = Int.MAX_VALUE

    var phase: Int = 0
    var lineSaid: Boolean = false
    var mageDown: Boolean = false
    val specials: NexSpecialCounter = NexSpecialCounter()
    val melee: NexMeleeChain = NexMeleeChain()
    var darkness: NexDarknessState? = null
    var reaver: Npc? = null
    var siphonFrom: Int = Int.MAX_VALUE
    var siphonUntil: Int = Int.MIN_VALUE

    val inProgress: Boolean
        get() = nex != null

    val phaseRow: NexPhasesRow?
        get() = PHASES.getOrNull(phase)

    fun mageReleased(mage: Npc): Boolean {
        val index = mages.indexOfFirst { it === mage }
        return index in 0 until phase || (index == phase && lineSaid)
    }

    fun mageOf(phaseIndex: Int): Npc? = mages.getOrNull(phaseIndex)

    companion object {
        val PHASES: List<NexPhasesRow> by lazy {
            NexPhasesRow.all().sortedByDescending { it.threshold }
        }
    }
}
