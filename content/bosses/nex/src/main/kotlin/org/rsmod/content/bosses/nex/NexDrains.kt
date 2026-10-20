package org.rsmod.content.bosses.nex

internal object NexDrains {
    const val ATTACK = "stat.attack"
    const val RANGED = "stat.ranged"
    const val MAGIC = "stat.magic"

    fun drainedStat(melee: Int, ranged: Int, magic: Int): String =
        when {
            ranged > melee && ranged >= magic -> RANGED
            magic > melee && magic > ranged -> MAGIC
            else -> ATTACK
        }
}
