package org.rsmod.content.bosses.nex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NexLootRollTest {
    private val commons =
        listOf(
            NexDrop("obj.big_bones", 1),
            NexDrop("obj.4dosepotionofsaradomin", 10),
            NexDrop("obj.4dose2restore", 5),
            NexDrop("obj.bloodrune", 629),
        )
    private val rares = listOf(NexDrop("obj.broken_torva_chest", 1))

    @Test
    fun `each rolled drop is handed out exactly once`() {
        val roll = NexLootRoll(true, commons, rares, pet = true)
        val ground = HashMap<String, Int>()
        repeat(3) {
            roll.takeCommons().forEach { ground.merge(it.obj, it.count, Int::plus) }
            roll.takeRares().forEach { ground.merge(it.obj, it.count, Int::plus) }
        }
        val expected = (commons + rares).associate { it.obj to it.count }
        assertEquals(expected, ground)
    }

    @Test
    fun `a second payout of the same roll gives nothing`() {
        val roll = NexLootRoll(false, commons.drop(1), rares, pet = false)
        assertEquals(3, roll.takeCommons().size)
        assertTrue(roll.takeCommons().isEmpty())
        assertEquals(rares, roll.takeRares())
        assertTrue(roll.takeRares().isEmpty())
    }

    @Test
    fun `the pet is given at most once`() {
        val roll = NexLootRoll(true, emptyList(), emptyList(), pet = true)
        assertTrue(roll.takePet())
        assertFalse(roll.takePet())
        assertFalse(NexLootRoll(true, emptyList(), emptyList(), pet = false).takePet())
    }

    @Test
    fun `commons and rares are independent so paying one keeps the other`() {
        val roll = NexLootRoll(true, commons, rares, pet = false)
        assertEquals(rares, roll.takeRares())
        assertEquals(commons, roll.takeCommons())
    }

    @Test
    fun `stack sized drops survive the payout unchanged`() {
        val big = listOf(NexDrop("obj.coins", Int.MAX_VALUE))
        val roll = NexLootRoll(false, big, emptyList(), pet = false)
        assertEquals(Int.MAX_VALUE, roll.takeCommons().single().count)
    }
}
