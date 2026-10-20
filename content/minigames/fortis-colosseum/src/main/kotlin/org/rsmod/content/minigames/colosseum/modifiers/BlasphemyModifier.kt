package org.rsmod.content.minigames.colosseum.modifiers

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.stat.statSub
import org.rsmod.content.minigames.colosseum.ColosseumModifiers
import org.rsmod.content.minigames.colosseum.Modifier
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit

@Singleton
internal class BlasphemyModifier @Inject constructor(private val modifiers: ColosseumModifiers) {
    fun onHit(player: Player, hit: Hit) {
        val level = modifiers.level(player, Modifier.Blasphemy)
        if (level <= 0) return
        val drain = BlasphemyRules.drain(hit.damage, level)
        if (drain > 0) player.statSub(PRAYER, constant = drain, percent = 0)
    }

    private companion object {
        private const val PRAYER = "stat.prayer"
    }
}
