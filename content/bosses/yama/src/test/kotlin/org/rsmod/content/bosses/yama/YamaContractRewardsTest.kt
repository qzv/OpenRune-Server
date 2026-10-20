package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class YamaContractRewardsTest {
    @Test
    fun `every contract pays its reward and returned contract exactly once`() {
        for (contract in YamaContract.entries) {
            val items = YamaContractRewards.items(contract, null)
            assertEquals(items.size, items.map { it.item }.toSet().size, contract.name)
            for (reward in contract.rewards) {
                assertEquals(1, items.count { it.item == reward.item }, contract.name)
            }
            val returned = items.count { it.item == contract.item }
            assertEquals(if (contract.returned) 1 else 0, returned, contract.name)
            val expected = contract.rewards.size + returned
            assertEquals(expected, items.size, contract.name)
        }
    }

    @Test
    fun `the chosen oathplate piece is added once on top of the contract rewards`() {
        val piece = "obj.oathplate_chest"
        val contract = YamaContract.OATHPLATE
        val without = YamaContractRewards.items(contract, null)
        val with = YamaContractRewards.items(contract, piece)
        assertEquals(1, with.count { it.item == piece && it.count == 1 })
        assertEquals(without.size + 1, with.size)
    }

    @Test
    fun `duo shares never pay out more than the rolled stack`() {
        for (count in 2..200) {
            for (step in 0..100) {
                val share = step / 100.0
                if (count * share < 1 || count * (1 - share) < 1) continue
                val total =
                    YamaLootShare.quantity(count, share) + YamaLootShare.quantity(count, 1 - share)
                assertTrue(total <= count, "count=$count share=$share")
            }
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
        }
    }
}
