package org.rsmod.api.player.hook

import dev.openrune.types.aconverted.interf.IfButtonOp
import org.rsmod.game.entity.Player

public fun interface PlayerWornButtonHook {
    /** @return `true` when the click was consumed and the default worn op must not run. */
    public fun intercept(player: Player, wornSlot: Int, op: IfButtonOp): Boolean
}
