package org.rsmod.api.game.process.npc

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class NpcRegenProcessorTest {
    private val processor = NpcRegenProcessor()

    @Test
    fun `a damaged npc restores a hitpoint every regen rate ticks`() {
        val npc = Npc(NPC, CoordGrid(3200, 3200))
        assertTrue(npc.regenRate > 0)
        npc.hitpoints -= DAMAGE

        val regenTicks = mutableListOf<Int>()
        for (tick in 1..npc.regenRate * 3) {
            val before = npc.hitpoints
            processor.process(npc)
            if (npc.hitpoints > before) regenTicks += tick
        }

        val rate = npc.regenRate
        assertEquals(listOf(1, 1 + rate, 1 + rate * 2), regenTicks)
        assertEquals(npc.baseHitpointsLvl - DAMAGE + 3, npc.hitpoints)
    }

    @Test
    fun `drained combat stats restore on the same ticks as hitpoints`() {
        val npc = Npc(NPC, CoordGrid(3200, 3200))
        npc.hitpoints -= DAMAGE
        npc.attackLvl -= DAMAGE

        repeat(npc.regenRate + 1) { processor.process(npc) }

        assertEquals(npc.baseAttackLvl - DAMAGE + 2, npc.attackLvl)
        assertEquals(npc.baseHitpointsLvl - DAMAGE + 2, npc.hitpoints)
    }

    companion object {
        private const val NPC = "npc.cowboss"
        private const val DAMAGE = 5

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
        }
    }
}
