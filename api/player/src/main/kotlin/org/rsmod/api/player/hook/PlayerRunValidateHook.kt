package org.rsmod.api.player.hook

import org.rsmod.game.entity.Player

public fun interface PlayerRunValidateHook {
    public fun validate(player: Player): String?
}
