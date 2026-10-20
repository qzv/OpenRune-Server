package org.rsmod.content.other.consumables.potion

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.PlayerHealCapHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.content.other.consumables.boostedHitpoints
import org.rsmod.content.other.consumables.healAmount
import org.rsmod.content.other.consumables.restoredHitpoints

@Singleton
class PotionHealing
@Inject
constructor(
    private val healCaps: Set<@JvmSuppressWildcards PlayerHealCapHook>,
) {
    fun boost(
        access: ProtectedAccess,
        constant: Int,
        percent: Int,
    ) {
        val player = access.player

        restoreTo(
            access = access,
            target =
                boostedHitpoints(
                    base = player.statBase(HITPOINTS),
                    current = player.stat(HITPOINTS),
                    constant = constant,
                    percent = percent,
                ),
        )
    }

    fun restore(
        access: ProtectedAccess,
        constant: Int,
        percent: Int,
    ) {
        val player = access.player

        restoreTo(
            access = access,
            target =
                restoredHitpoints(
                    base = player.statBase(HITPOINTS),
                    current = player.stat(HITPOINTS),
                    constant = constant,
                    percent = percent,
                ),
        )
    }

    fun restoreTo(
        access: ProtectedAccess,
        target: Int,
    ) {
        val amount =
            healCaps.healAmount(
                player = access.player,
                hitpoints = access.player.stat(HITPOINTS),
                target = target,
            )

        if (amount <= 0) {
            return
        }

        access.statAdd(
            stat = HITPOINTS,
            constant = amount,
            percent = 0,
        )
    }

    private companion object {
        const val HITPOINTS: String =
            "stat.hitpoints"
    }
}
