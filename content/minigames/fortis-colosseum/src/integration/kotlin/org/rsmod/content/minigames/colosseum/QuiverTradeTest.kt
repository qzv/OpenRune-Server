package org.rsmod.content.minigames.colosseum

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@OptIn(UncheckedType::class)
@Execution(ExecutionMode.SAME_THREAD)
class QuiverTradeTest {
    @Test
    fun GameTestState.`the trade swaps the quiver for splinters once`() =
        runInjectedGameTest(QuiverTrade::class) { trade ->
            player.clearInv()
            player.inv[3] = obj(QuiverTrade.QUIVER, 1)

            assertTrue(trade.tradeForSplinters(player, 3))
            assertFalse(trade.tradeForSplinters(player, 3))

            assertEquals(0, player.inv.count(QuiverTrade.QUIVER))
            assertEquals(QuiverTrade.SPLINTER_COUNT, player.inv.count(QuiverTrade.SPLINTERS))
        }

    @Test
    fun GameTestState.`a full inventory still trades into the quiver's slot`() =
        runInjectedGameTest(QuiverTrade::class) { trade ->
            player.clearInv()
            for (slot in 0 until player.inv.size) player.inv[slot] = obj(FILLER, 1)
            player.inv[0] = obj(QuiverTrade.QUIVER, 1)

            assertTrue(trade.tradeForSplinters(player, 0))

            assertEquals(0, player.inv.count(QuiverTrade.QUIVER))
            assertEquals(QuiverTrade.SPLINTER_COUNT, player.inv.count(QuiverTrade.SPLINTERS))
            assertEquals(player.inv.size - 1, player.inv.count(FILLER))
        }

    @Test
    fun GameTestState.`a splinter stack that would overflow keeps the quiver`() =
        runInjectedGameTest(QuiverTrade::class) { trade ->
            player.clearInv()
            player.inv[0] = obj(QuiverTrade.SPLINTERS, Int.MAX_VALUE - 1)
            player.inv[1] = obj(QuiverTrade.QUIVER, 1)

            assertFalse(trade.tradeForSplinters(player, 1))

            assertEquals(1, player.inv.count(QuiverTrade.QUIVER))
            assertEquals(Int.MAX_VALUE - 1, player.inv.count(QuiverTrade.SPLINTERS))
        }

    @Test
    fun GameTestState.`a quiver no longer in the chosen slot is not traded`() =
        runInjectedGameTest(QuiverTrade::class) { trade ->
            player.clearInv()
            player.inv[5] = obj(QuiverTrade.QUIVER, 1)

            assertFalse(trade.tradeForSplinters(player, 2))
            assertNull(trade.gamble(player, 2))

            assertEquals(1, player.inv.count(QuiverTrade.QUIVER))
            assertEquals(0, player.inv.count(QuiverTrade.SPLINTERS))
        }

    @Test
    fun GameTestState.`the gamble takes the quiver once`() =
        runInjectedGameTest(QuiverTrade::class) { trade ->
            player.clearInv()
            player.inv[0] = obj(QuiverTrade.QUIVER, 1)

            assertNotNull(trade.gamble(player, 0))
            assertNull(trade.gamble(player, 0))

            assertEquals(0, player.inv.count(QuiverTrade.QUIVER))
            assertEquals(0, player.inv.count(QuiverTrade.SPLINTERS))
        }

    private fun obj(name: String, count: Int): InvObj = InvObj(name.asRSCM(RSCMType.OBJ), count)

    private fun Inventory.count(name: String): Int =
        objs.filterNotNull().filter { it.id == name.asRSCM(RSCMType.OBJ) }.sumOf { it.count }

    private companion object {
        private const val FILLER = "obj.bronze_dagger"
    }
}
