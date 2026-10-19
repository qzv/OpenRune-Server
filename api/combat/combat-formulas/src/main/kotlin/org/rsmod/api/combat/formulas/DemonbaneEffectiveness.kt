package org.rsmod.api.combat.formulas

import dev.openrune.types.NpcServerType
import org.rsmod.api.config.refs.params

internal object DemonbaneEffectiveness {
    const val STANDARD_PERCENT: Int = 100
    const val RESISTANT_PERCENT: Int = 70

    const val ARCLIGHT_BONUS: Int = 70
    const val BURNING_CLAWS_BONUS: Int = 10
    const val SILVERLIGHT_BONUS: Int = 30
    const val SCORCHING_BOW_BONUS: Int = 30

    fun percent(type: NpcServerType): Int {
        val effectiveness = type.param(params.demonbane_effectiveness)
        return when {
            effectiveness != 0 -> effectiveness
            type.param(params.demonbane_resistant) != 0 -> RESISTANT_PERCENT
            else -> STANDARD_PERCENT
        }
    }

    fun scaleBonus(bonus: Int, percent: Int): Int = scale(bonus, percent, divisor = 100)

    fun scaleBoostOnly(bonus: Int, percent: Int): Int =
        if (percent > STANDARD_PERCENT) scaleBonus(bonus, percent) else bonus
}
