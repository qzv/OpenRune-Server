package org.rsmod.api.game.process.npc

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.StepFactory
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntityAvatar
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcChaseSlot
import org.rsmod.game.movement.RouteRequestPathingEntity
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class NpcChaseSlotTest {
    private val start = CoordGrid(3200, 3200)
    private val collision = CollisionFlagMap()
    private val movement =
        NpcMovementProcessor(collision, StepFactory(collision), RouteFactory(collision), EventBus())
    private val npc = Npc("npc.hans", start)
    private val target = Player().apply { coords = start.translate(0, 8) }

    init {
        for (x in 3192..3216 step 8) {
            for (z in 3192..3216 step 8) {
                collision.allocateIfAbsent(x, z, 0)
            }
        }
    }

    @Test
    fun `a chase heads for the slot instead of the target`() {
        val slot = start.translate(3, 6)
        npc.chaseSlot = fixedSlot(slot)
        movement.consumeRequest(npc, RouteRequestPathingEntity(target.avatar))
        assertEquals(listOf(slot), npc.routeDestination.toList())
    }

    @Test
    fun `a chase without a slot heads for the target`() {
        npc.chaseSlot = fixedSlot(null)
        movement.consumeRequest(npc, RouteRequestPathingEntity(target.avatar))
        assertEquals(listOf(target.coords.translateZ(-1)), npc.routeDestination.toList())
    }

    @Test
    fun `a chase without a slot provider heads for the target`() {
        movement.consumeRequest(npc, RouteRequestPathingEntity(target.avatar))
        assertEquals(listOf(target.coords.translateZ(-1)), npc.routeDestination.toList())
    }

    @Test
    fun `a routefinding npc paths around an obstacle`() {
        for (x in 3196..3204) {
            collision.add(x, 3204, 0, CollisionFlag.LOC)
        }
        npc.routefinds = true
        movement.consumeRequest(npc, RouteRequestPathingEntity(target.avatar))
        assertTrue(npc.routeDestination.size > 1)
        assertTrue(npc.routeDestination.toList().none { it.z == 3204 && it.x in 3196..3204 })
    }

    @Test
    fun `a stepping npc keeps a single destination`() {
        for (x in 3196..3204) {
            collision.add(x, 3204, 0, CollisionFlag.LOC)
        }
        movement.consumeRequest(npc, RouteRequestPathingEntity(target.avatar))
        assertEquals(1, npc.routeDestination.size)
    }

    private fun fixedSlot(slot: CoordGrid?) =
        object : NpcChaseSlot {
            override fun slot(npc: Npc, target: PathingEntityAvatar): CoordGrid? = slot
        }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
        }
    }
}
