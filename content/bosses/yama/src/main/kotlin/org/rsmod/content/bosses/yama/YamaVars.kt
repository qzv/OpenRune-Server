package org.rsmod.content.bosses.yama

import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal var Player.throneState by intVarBit("varbit.yama_throne_state")

internal var Player.voiceProgress by intVarBit("varbit.chasm_voice_progress")

internal var Player.contractSigned by intVarBit("varbit.yama_imp_contract_signed")

internal var Player.transitionPhase by intVarBit("varbit.yama_transition_phase")

internal var Player.transitionDamageTaken by intVarBit("varbit.yama_transition_damage_taken")

internal var Player.burnTotal by intVarBit("varbit.player_own_burn_damage_total")

internal var Player.burnSize by intVarBit("varbit.player_own_burn_damage_hitsplat_size")

internal var Player.totalYamaKills by intVarp("varp.total_yama_kills")

internal var Player.specialEnergy by intVarp("varp.sa_energy")

internal var Player.yamaBestTime by intVarp("varp.yama_best_time")

internal var Player.runInvertMode by intVarBit("varbit.runinvert_mode")
