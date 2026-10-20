package org.rsmod.content.bosses.nex

internal object NexDrains {
    const val ATTACK = "stat.attack"
    const val RANGED = "stat.ranged"
    const val MAGIC = "stat.magic"
    private const val BLIND_PERCENT = 15
    private const val PERCENT = 100

    fun drainedStat(melee: Int, ranged: Int, magic: Int): String =
        when {
            ranged > melee && ranged >= magic -> RANGED
            magic > melee && magic > ranged -> MAGIC
            else -> ATTACK
        }

    fun blindDrain(attack: Int): Int = (attack * BLIND_PERCENT + PERCENT - 1) / PERCENT
}
