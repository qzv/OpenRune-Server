package org.rsmod.content.minigames.colosseum

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@OptIn(UncheckedType::class)
@Execution(ExecutionMode.SAME_THREAD)
class ColosseumRewardsTest {
    @Test
    fun GameTestState.`take notes normal loot and moves it out of the chest`() =
        runInjectedGameTest(ColosseumRewards::class) { rewards ->
            player.clearInv()
            val chest = rewards.earned(player)
            player.clearInv(chest)
            chest[0] = obj(PLATEBODY, 1)
            with(rewards) { player.withProtectedAccess { take(0, 1) } }
            assertEquals(0, player.inv.count(PLATEBODY))
            assertEquals(1, player.inv.count(noted(PLATEBODY)))
            assertTrue(chest.objs.all { it == null })
        }

    @Test
    fun GameTestState.`uniques are taken unnoted`() =
        runInjectedGameTest(ColosseumRewards::class) { rewards ->
            player.clearInv()
            val chest = rewards.earned(player)
            player.clearInv(chest)
            chest[0] = obj(HELM, 1)
            with(rewards) { player.withProtectedAccess { take(0, 1) } }
            assertEquals(1, player.inv.count(HELM))
            assertTrue(chest.objs.all { it == null })
        }

    @Test
    fun GameTestState.`take into a full inventory leaves the chest untouched`() =
        runInjectedGameTest(ColosseumRewards::class) { rewards ->
            fillInv(player.inv.size)
            val chest = rewards.earned(player)
            player.clearInv(chest)
            chest[0] = obj(SPLINTERS, 500)
            with(rewards) { player.withProtectedAccess { take(0, Int.MAX_VALUE) } }
            assertEquals(500, chest.count(SPLINTERS))
            assertEquals(0, player.inv.count(SPLINTERS))
            assertMessageSent("You don't have enough inventory space to take any.")
        }

    @Test
    fun GameTestState.`take never moves more than the stack holds`() =
        runInjectedGameTest(ColosseumRewards::class) { rewards ->
            player.clearInv()
            val chest = rewards.earned(player)
            player.clearInv(chest)
            chest[0] = obj(SPLINTERS, 80)
            with(rewards) {
                player.withProtectedAccess { take(0, Int.MAX_VALUE) }
                player.withProtectedAccess { take(0, Int.MAX_VALUE) }
            }
            assertEquals(80, player.inv.count(SPLINTERS))
            assertTrue(chest.objs.all { it == null })
        }

    @Test
    fun GameTestState.`take into a stack near the maximum loses nothing`() =
        runInjectedGameTest(ColosseumRewards::class) { rewards ->
            fillInv(player.inv.size - 1)
            player.inv[player.inv.size - 1] = obj(SPLINTERS, Int.MAX_VALUE - 10)
            val chest = rewards.earned(player)
            player.clearInv(chest)
            chest[0] = obj(SPLINTERS, 80)
            with(rewards) { player.withProtectedAccess { take(0, Int.MAX_VALUE) } }
            val held = player.inv.count(SPLINTERS).toLong()
            assertEquals(Int.MAX_VALUE.toLong() - 10 + 80, held + chest.count(SPLINTERS))
        }

    @Test
    fun GameTestState.`take all with one free slot keeps the rest in the chest`() =
        runInjectedGameTest(ColosseumRewards::class) { rewards ->
            fillInv(player.inv.size - 1)
            val chest = rewards.earned(player)
            player.clearInv(chest)
            chest[0] = obj(PLATEBODY, 1)
            chest[1] = obj(KITESHIELD, 4)
            chest[2] = obj(SPLINTERS, 150)
            with(rewards) { player.withProtectedAccess { takeAll(player.inv) } }
            assertEquals(1, total(player.inv, PLATEBODY) + total(chest, PLATEBODY))
            assertEquals(4, total(player.inv, KITESHIELD) + total(chest, KITESHIELD))
            assertEquals(150, total(player.inv, SPLINTERS) + total(chest, SPLINTERS))
            assertEquals(2, chest.objs.count { it != null })
        }

    @Test
    fun GameTestState.`repeated take all does not duplicate loot`() =
        runInjectedGameTest(ColosseumRewards::class) { rewards ->
            player.clearInv()
            val chest = rewards.earned(player)
            player.clearInv(chest)
            chest[0] = obj(PLATEBODY, 1)
            chest[1] = obj(KITESHIELD, 4)
            chest[2] = obj(SPLINTERS, 150)
            with(rewards) {
                player.withProtectedAccess { takeAll(player.inv) }
                player.withProtectedAccess { takeAll(player.inv) }
            }
            advance(ticks = 2)
            assertEquals(1, total(player.inv, PLATEBODY))
            assertEquals(4, total(player.inv, KITESHIELD))
            assertEquals(150, total(player.inv, SPLINTERS))
            assertTrue(chest.objs.all { it == null })
        }

    @Test
    fun GameTestState.`take all to the bank deposits everything unnoted`() =
        runInjectedGameTest(ColosseumRewards::class) { rewards ->
            player.clearInv()
            val chest = rewards.earned(player)
            player.clearInv(chest)
            chest[0] = obj(PLATEBODY, 1)
            chest[1] = obj(SPLINTERS, 150)
            var bank: Inventory? = null
            with(rewards) {
                player.withProtectedAccess {
                    bank = this.bank
                    player.clearInv(this.bank)
                    takeAll(this.bank)
                }
            }
            val deposited = checkNotNull(bank)
            assertEquals(1, deposited.count(PLATEBODY))
            assertEquals(150, deposited.count(SPLINTERS))
            assertEquals(0, player.inv.count(PLATEBODY) + player.inv.count(SPLINTERS))
            assertTrue(chest.objs.all { it == null })
        }

    @Test
    fun GameTestState.`banking a wave adds the rolled loot exactly once`() =
        runInjectedGameTest(ColosseumRewards::class) { rewards ->
            rewards.reset(player)
            rewards.future(player)[0] = obj(SPLINTERS, 80)
            rewards.bankWave(player)
            assertEquals(80, rewards.previous(player).count(SPLINTERS))
            rewards.bankWave(player)
            assertEquals(80, rewards.earned(player).count(SPLINTERS))
            assertTrue(rewards.future(player).objs.all { it == null })
        }

    private fun GameTestScope.fillInv(slots: Int) {
        player.clearInv()
        for (slot in 0 until slots) player.inv[slot] = obj(FILLER, 1)
    }

    private fun obj(name: String, count: Int): InvObj = InvObj(name.asRSCM(RSCMType.OBJ), count)

    private fun noted(name: String): Int =
        checkNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))).certlink

    private fun Inventory.count(name: String): Int = count(name.asRSCM(RSCMType.OBJ))

    private fun Inventory.count(id: Int): Int =
        objs.filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun total(inv: Inventory, name: String): Int = inv.count(name) + inv.count(noted(name))

    private companion object {
        private const val PLATEBODY = "obj.rune_platebody"
        private const val KITESHIELD = "obj.rune_kiteshield"
        private const val SPLINTERS = "obj.sunfiresplinter"
        private const val HELM = "obj.sunfire_helm"
        private const val FILLER = "obj.bronze_dagger"
    }
}
