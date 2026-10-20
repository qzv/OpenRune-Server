package org.rsmod.content.minigames.colosseum.enemies

import org.rsmod.api.table.ColosseumWarbandSlotsRow
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntityAvatar
import org.rsmod.game.entity.npc.NpcChaseSlot
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

internal class WarbandSlots(private val collision: CollisionFlagMap) : NpcChaseSlot {
    private val offsets: Map<Int, List<Pair<Int, Int>>> by lazy {
        ColosseumWarbandSlotsRow.all().associate { it.npc.id to it.offsetX.zip(it.offsetZ) }
    }
    private val members = HashMap<Int, MutableSet<Npc>>()

    val npcIds: Set<Int>
        get() = offsets.keys

    fun add(npc: Npc) {
        members.values.forEach { peers -> peers.removeIf { !it.isSlotAssigned } }
        members.getOrPut(npc.type.id, ::HashSet) += npc
    }

    fun remove(npc: Npc) {
        members[npc.type.id]?.remove(npc)
    }

    override fun slot(npc: Npc, target: PathingEntityAvatar): CoordGrid? {
        val order = offsets[npc.type.id] ?: return null
        for ((dx, dz) in order) {
            val tile = target.coords.translate(dx, dz)
            if (tile == npc.coords) return tile
            if (!collision.isWalkBlocked(tile) && !claimedByPeer(npc, tile)) return tile
        }
        return null
    }

    override fun holds(npc: Npc, target: PathingEntityAvatar): Boolean =
        offsets[npc.type.id].orEmpty().any { (dx, dz) ->
            target.coords.translate(dx, dz) == npc.coords
        }

    private fun claimedByPeer(npc: Npc, tile: CoordGrid): Boolean =
        members[npc.type.id].orEmpty().any { it !== npc && it.isSlotAssigned && it.coords == tile }
}
