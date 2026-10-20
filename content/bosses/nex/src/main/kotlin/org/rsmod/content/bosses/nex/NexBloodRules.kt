package org.rsmod.content.bosses.nex

internal object NexBloodRules {
    private const val PERCENT = 100
    private const val STANDARD_HEAL_PERCENT = 115
    private const val SACRIFICE_HEAL_PERCENT = 150
    private const val SACRIFICE_PRAYER_DIVISOR = 4
    private const val SACRIFICE_ESCAPE_DISTANCE = 7

    fun standardHeal(damage: Int): Int = damage * STANDARD_HEAL_PERCENT / PERCENT

    fun sacrificeHeal(damage: Int): Int = damage * SACRIFICE_HEAL_PERCENT / PERCENT

    fun sacrificePrayerDrain(prayer: Int): Int = prayer / SACRIFICE_PRAYER_DIVISOR

    fun escapedSacrifice(distance: Int): Boolean = distance >= SACRIFICE_ESCAPE_DISTANCE

    fun siphoning(cycle: Int, from: Int, until: Int): Boolean = cycle in from..until
}
