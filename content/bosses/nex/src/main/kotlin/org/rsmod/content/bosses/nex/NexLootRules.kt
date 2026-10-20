package org.rsmod.content.bosses.nex

internal data class NexAward(val share: Double, val team: Int, val mvp: Boolean)

internal object NexLootRules {
    const val SCALE = 1_000_000
    const val UNIQUE_RATE = 43
    private const val MIN_DAMAGE = 1
    private const val MVP_PERCENT = 110
    private const val PERCENT = 100
    private const val TENTHS_PER_TICK = 6
    private const val TENTHS = 10
    private const val SECONDS_PER_MINUTE = 60

    fun <K> awards(damage: Map<K, Int>, nexDamage: Map<K, Int>): Map<K, NexAward> {
        val eligible = damage.filterValues { it >= MIN_DAMAGE }
        if (eligible.isEmpty()) return emptyMap()
        val total = eligible.values.sumOf { it.toLong() }.toDouble()
        val mvp =
            eligible.keys
                .filter { (nexDamage[it] ?: 0) > 0 }
                .maxByOrNull { nexDamage[it] ?: 0 }
        return eligible.mapValues { (key, dealt) ->
            NexAward(dealt / total, eligible.size, key == mvp)
        }
    }

    fun quantity(count: Int, award: NexAward, stackable: Boolean): Int {
        if (count <= 0) return 0
        val bonus = if (award.mvp) MVP_PERCENT else PERCENT
        val scaled = count.toDouble() * award.share * award.team * bonus / PERCENT
        val amount = scaled.toLong().coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        return if (stackable) amount else minOf(amount, count)
    }

    fun hits(award: NexAward, rate: Int, roll: Int): Boolean {
        if (rate <= 0) return false
        val bonus = if (award.mvp) MVP_PERCENT else PERCENT
        val chance = award.share * bonus / PERCENT
        return roll < (chance * SCALE).toLong().coerceAtMost(rate.toLong() * SCALE)
    }

    private fun seconds(ticks: Int): Int =
        (ticks.coerceAtLeast(0) * TENTHS_PER_TICK + TENTHS / 2) / TENTHS

    fun clock(ticks: Int): String {
        val seconds = seconds(ticks)
        return "${seconds / SECONDS_PER_MINUTE}:${"%02d".format(seconds % SECONDS_PER_MINUTE)}"
    }
}
