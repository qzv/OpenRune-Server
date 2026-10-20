package org.rsmod.content.bosses.nex

internal enum class NexSpecial {
    Virus,
    Dash,
    Smash,
    Embrace,
    Siphon,
    Sacrifice,
}

internal class NexSpecialCounter {
    var used: Int = 0
        private set

    fun next(slots: List<NexSpecial>): NexSpecial = slots[used++ % slots.size]

    fun restart(first: Int) {
        used = first
    }

    companion object {
        val SMOKE: List<NexSpecial> = listOf(NexSpecial.Virus, NexSpecial.Dash)
        val SHADOW: List<NexSpecial> = listOf(NexSpecial.Smash, NexSpecial.Embrace)
        val BLOOD: List<NexSpecial> = listOf(NexSpecial.Siphon, NexSpecial.Sacrifice)
    }
}

internal class NexMeleeChain {
    var length: Int = 0
        private set

    fun melee(inReach: Boolean, start: () -> Boolean): Boolean {
        if (!inReach || length >= CHAIN_LENGTH) {
            length = 0
            return false
        }
        if (length == 0 && !start()) return false
        length++
        return true
    }

    companion object {
        const val CHAIN_LENGTH = 3
    }
}
