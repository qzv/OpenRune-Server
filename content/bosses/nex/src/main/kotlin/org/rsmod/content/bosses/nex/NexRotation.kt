package org.rsmod.content.bosses.nex

internal enum class NexSpecial {
    Virus,
    Dash,
    Smash,
    Embrace,
}

internal class NexSpecialCounter {
    var used: Int = 0
        private set

    fun next(slots: List<NexSpecial>): NexSpecial = slots[used++ % slots.size]

    companion object {
        val SMOKE: List<NexSpecial> = listOf(NexSpecial.Virus, NexSpecial.Dash)
        val SHADOW: List<NexSpecial> = listOf(NexSpecial.Smash, NexSpecial.Embrace)
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
