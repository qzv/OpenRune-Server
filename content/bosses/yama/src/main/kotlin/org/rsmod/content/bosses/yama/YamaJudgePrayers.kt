package org.rsmod.content.bosses.yama

import org.rsmod.game.hit.HitType

internal class PrayerOutcome(val damage: Int, val protectsMagic: Boolean)

internal object YamaJudgePrayers {
    const val PROTECTED_PERCENT = 447
    const val PERCENT = 1000

    fun resolve(protectsMagic: Boolean, style: HitType, damage: Int): PrayerOutcome {
        if (style != HitType.Melee && style != HitType.Ranged && style != HitType.Magic) {
            return PrayerOutcome(damage, protectsMagic)
        }
        val protected = style == HitType.Ranged || (style == HitType.Magic) == protectsMagic
        if (protected) return PrayerOutcome(damage * PROTECTED_PERCENT / PERCENT, protectsMagic)
        return PrayerOutcome(damage, style == HitType.Magic)
    }
}
