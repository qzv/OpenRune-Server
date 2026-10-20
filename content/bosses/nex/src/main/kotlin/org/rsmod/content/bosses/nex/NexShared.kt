package org.rsmod.content.bosses.nex

import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

internal const val PRAYER = "stat.prayer"
internal const val FREEZE_TIMER = "timer.combat_freeze"
internal const val MAGE_CAST_SEQ = "seq.zaros_vertical_casting"
internal const val NEX_IMMUNE = "Nex is immune to your attacks!"
internal const val INJURED_MESSAGE = "You've been injured and can't use protection prayers!"

internal var Player.nexBarrier by intVarBit("varbit.nex_barrier")
