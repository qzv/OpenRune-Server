package org.rsmod.content.bosses.nex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NexRotationTest {
    @Test
    fun `smoke specials alternate starting with the virus`() {
        val counter = NexSpecialCounter()
        val specials = List(4) { counter.next(NexSpecialCounter.SMOKE) }
        assertEquals(
            listOf(NexSpecial.Virus, NexSpecial.Dash, NexSpecial.Virus, NexSpecial.Dash),
            specials,
        )
    }

    @Test
    fun `a smoke phase ending on the virus opens shadow with embrace darkness`() {
        val counter = NexSpecialCounter()
        repeat(3) { counter.next(NexSpecialCounter.SMOKE) }
        assertEquals(NexSpecial.Embrace, counter.next(NexSpecialCounter.SHADOW))
        assertEquals(NexSpecial.Smash, counter.next(NexSpecialCounter.SHADOW))
    }

    @Test
    fun `a smoke phase ending on the dash opens shadow with shadow smash`() {
        val counter = NexSpecialCounter()
        repeat(2) { counter.next(NexSpecialCounter.SMOKE) }
        assertEquals(NexSpecial.Smash, counter.next(NexSpecialCounter.SHADOW))
        assertEquals(NexSpecial.Embrace, counter.next(NexSpecialCounter.SHADOW))
    }

    @Test
    fun `melee chains last three attacks then force a cast`() {
        val chain = NexMeleeChain()
        val picks = List(5) { chain.melee(inReach = true) { true } }
        assertEquals(listOf(true, true, true, false, true), picks)
    }

    @Test
    fun `a chain only starts when the start roll succeeds`() {
        val chain = NexMeleeChain()
        assertFalse(chain.melee(inReach = true) { false })
        assertEquals(0, chain.length)
        assertTrue(chain.melee(inReach = true) { true })
        assertTrue(chain.melee(inReach = true) { false })
    }

    @Test
    fun `leaving reach casts and resets the chain`() {
        val chain = NexMeleeChain()
        chain.melee(inReach = true) { true }
        chain.melee(inReach = true) { true }
        assertFalse(chain.melee(inReach = false) { true })
        assertEquals(0, chain.length)
        assertTrue(chain.melee(inReach = true) { true })
        assertEquals(1, chain.length)
    }

    @Test
    fun `blood specials alternate from the randomly chosen first one`() {
        val counter = NexSpecialCounter()
        repeat(5) { counter.next(NexSpecialCounter.SHADOW) }
        counter.restart(1)
        val specials = List(3) { counter.next(NexSpecialCounter.BLOOD) }
        assertEquals(
            listOf(NexSpecial.Sacrifice, NexSpecial.Siphon, NexSpecial.Sacrifice),
            specials,
        )
        counter.restart(0)
        assertEquals(NexSpecial.Siphon, counter.next(NexSpecialCounter.BLOOD))
    }

    @Test
    fun `ice specials alternate from the randomly chosen first one`() {
        val counter = NexSpecialCounter()
        counter.restart(0)
        val specials = List(3) { counter.next(NexSpecialCounter.ICE) }
        assertEquals(listOf(NexSpecial.Prison, NexSpecial.Contain, NexSpecial.Prison), specials)
    }
}
