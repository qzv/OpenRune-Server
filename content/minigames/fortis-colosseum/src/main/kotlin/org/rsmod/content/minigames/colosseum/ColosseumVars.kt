package org.rsmod.content.minigames.colosseum

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal enum class RunState {
    None,
    AwaitingStart,
    Intermission,
    WaveActive,
    Finished,
    Entering,
    Leaving,
}

internal var Player.runWave by intVarp("varp.colosseum_run_wave")
private var Player.runStateId by intVarp("varp.colosseum_run_state")
internal var Player.ownedModifiers by intVarp("varp.colosseum_owned_modifiers")
internal var Player.offerWave by intVarp("varp.colosseum_offer_wave")

internal var Player.runState: RunState
    get() = RunState.entries.getOrElse(runStateId) { RunState.None }
    set(value) {
        runStateId = value.ordinal
    }

internal var Player.offerA by intVarp("varp.if4")
internal var Player.offerB by intVarp("varp.if5")
internal var Player.offerC by intVarp("varp.if6")
internal var Player.selectedModifier by intVarBit("varbit.colosseum_selected_modifier")
internal var Player.entryWarningDisabled by boolVarBit("varbit.colosseum_entry_warning")

internal var Player.currentGlory by intVarp("varp.colosseum_current_glory")
internal var Player.bestGlory by intVarp("varp.colosseum_glory")
internal var Player.lastModifierGlory by intVarp("varp.colosseum_last_modifier_glory")
internal var Player.waveDamageTaken by intVarp("varp.colosseum_wave_damage_taken")
internal var Player.waveStartTime by intVarp("varp.colosseum_wave_start_time")
internal var Player.lastWaveDuration by intVarp("varp.colosseum_last_wave_duration")
internal var Player.totalDuration by intVarp("varp.colosseum_total_duration")
internal var Player.totalWavesCompleted by intVarp("varp.total_colosseum_waves_completed")
internal var Player.highestWave by intVarBit("varbit.colosseum_highest_wave")
internal var Player.lootOpened by boolVarBit("varbit.colosseum_loot_opened")
internal var Player.killTimeBest by intVarBit("varbit.colosseum_killtime_best")
internal var Player.killTimeLatest by intVarBit("varbit.colosseum_killtime_latest")
internal var Player.solKills by intVarp("varp.total_sol_kills")

internal var Player.minimusIntroSeen by boolVarBit("varbit.colosseum_master_intro")
