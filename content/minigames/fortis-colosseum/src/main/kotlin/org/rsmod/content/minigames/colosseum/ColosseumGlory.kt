package org.rsmod.content.minigames.colosseum

import org.rsmod.api.enums.ColosseumEnums
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

private var Player.preciseTiming by intVarBit("varbit.option_precise_timing")

internal object ColosseumRank {
    private const val UNKNOWN_TITLE = "Stranger"
    private val THRESHOLDS = intArrayOf(2000, 5000, 8000, 12000, 16000, 20000)

    fun index(glory: Int): Int = THRESHOLDS.count { glory >= it }

    fun title(glory: Int): String =
        ColosseumEnums.colosseum_glory_titles.backing[index(glory)] ?: UNKNOWN_TITLE
}

internal object ColosseumGlory {
    private const val WAVE_GLORY = 100
    private const val SOL_BONUS = 1000
    private const val SPEED_PAR_TICKS = 500
    private const val FLAWLESS_GLORY = 100
    private const val TICK_MILLIS = 600
    private const val MILLIS_PER_SECOND = 1000
    private const val SECONDS_PER_MINUTE = 60
    private const val CENTIS_PER_MINUTE = 6000
    private const val CENTIS_PER_HOUR = 360000

    fun earned(wave: Int, durationTicks: Int, damageTaken: Int, modifierGlory: Int): Int {
        val complete = WAVE_GLORY * wave + if (wave == ColosseumModifiers.SOL_WAVE) SOL_BONUS else 0
        val speed =
            if (durationTicks in 1 until SPEED_PAR_TICKS) (SPEED_PAR_TICKS - durationTicks) * wave
            else 0
        val flawless = if (damageTaken <= 0) FLAWLESS_GLORY * wave else 0
        return complete + speed + modifierGlory + flawless
    }

    fun formatDuration(player: Player, ticks: Int): String =
        if (player.preciseTiming == 0) formatSeconds(ticks) else formatDuration(ticks)

    fun formatSeconds(ticks: Int): String {
        val seconds = (ticks * TICK_MILLIS + MILLIS_PER_SECOND / 2) / MILLIS_PER_SECOND
        return "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
    }

    fun formatDuration(ticks: Int): String {
        val centis = ticks * 60
        val hours = centis / CENTIS_PER_HOUR
        val minutes = centis % CENTIS_PER_HOUR / CENTIS_PER_MINUTE
        val seconds = centis % CENTIS_PER_MINUTE / 100
        val hundredths = centis % 100
        if (hours > 0) return "%d:%02d:%02d.%02d".format(hours, minutes, seconds, hundredths)
        return "%d:%02d.%02d".format(minutes, seconds, hundredths)
    }
}
