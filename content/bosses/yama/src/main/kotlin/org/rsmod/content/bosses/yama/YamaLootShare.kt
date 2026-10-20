package org.rsmod.content.bosses.yama

internal object YamaLootShare {
    const val SCALE = 1_000_000

    fun of(damage: Int, maxHitpoints: Int): Double =
        if (maxHitpoints <= 0) 0.0 else (damage.toDouble() / maxHitpoints).coerceIn(0.0, 1.0)

    fun quantity(count: Int, share: Double): Int = maxOf(1, (count * share).toInt())

    fun keepsUnique(share: Double, roll: Int): Boolean = roll < (share * SCALE).toInt()
}
