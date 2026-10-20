package org.rsmod.api.game.process.npc

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcAvatar
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.entity.util.EntityFaceTarget
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class NpcFaceEntityFallbackAngleTest {
    private val npcList = NpcList()
    private val playerList = PlayerList()
    private val collision = CollisionFlagMap()
    private val registry = NpcRegistry(npcList, collision, EventBus())
    private val postTick =
        NpcPostTickProcess(npcList, playerList, registry, NpcFaceSquareProcessor()) { t, _ ->
            throw t
        }

    @Test
    fun `face player sends the angle towards the player`() {
        val npc = spawn(CoordGrid(1820, 3103, 0), size = 5)
        val player = addPlayer(CoordGrid(1821, 3102, 0))

        npc.facePlayer(player)
        postTick.process()

        assertEquals(listOf(EntityFaceTarget(player).entitySlot to 104), npc.faces())
    }

    @Test
    fun `face player uses the player's tile from before the player moved`() {
        val npc = spawn(CoordGrid(1831, 3107, 0), size = 3)
        val start = CoordGrid(1827, 3106, 0)
        val player = addPlayer(start.translateX(1))
        player.previousCoords = start

        npc.facePlayer(player)
        postTick.process()

        assertEquals(listOf(EntityFaceTarget(player).entitySlot to 387), npc.faces())
    }

    @Test
    fun `face npc sends the angle towards the npc`() {
        val target = spawn(CoordGrid(1823, 3104, 0), size = 5)
        val npc = spawn(CoordGrid(1822, 3103, 0), size = 1)

        npc.faceNpc(target)
        postTick.process()

        assertEquals(listOf(EntityFaceTarget(target).entitySlot to 1280), npc.faces())
    }

    @Test
    fun `a face change is sent once at the end of the cycle`() {
        val npc = spawn(CoordGrid(1820, 3103, 0), size = 5)
        val player = addPlayer(CoordGrid(1821, 3102, 0))
        npc.facePlayer(player)
        postTick.process()

        npc.resetFaceEntity()
        npc.facePlayer(player)
        npc.resetFaceEntity()
        postTick.process()
        postTick.process()

        val faces = npc.faces()
        assertEquals(2, faces.size)
        assertEquals(EntityFaceTarget.NULL.entitySlot, faces.last().first)
    }

    private fun spawn(coords: CoordGrid, size: Int): Npc {
        collision.allocateIfAbsent(coords.x, coords.z, coords.level)
        val type = checkNotNull(ServerCacheManager.getNpc(NPC.asRSCM(RSCMType.NPC)))
        val npc = Npc(type, NpcAvatar(size)).apply { this.coords = coords }
        registry.add(npc)
        npc.infoProtocol = FaceRecorder()
        return npc
    }

    private fun addPlayer(coords: CoordGrid): Player {
        val player = Player().apply { this.coords = coords }
        player.previousCoords = coords
        player.slotId = PLAYER_SLOT
        playerList[PLAYER_SLOT] = player
        return player
    }

    private fun Npc.faces(): List<Pair<Int, Int>> = (infoProtocol as FaceRecorder).faces

    private class FaceRecorder : NpcInfoProtocol by NoopNpcInfo {
        val faces = mutableListOf<Pair<Int, Int>>()

        override fun setFacePathingEntity(slot: Int, fallbackAngle: Int) {
            faces += slot to fallbackAngle
        }
    }

    companion object {
        private const val NPC = "npc.hans"
        private const val PLAYER_SLOT = 1

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
        }
    }
}
