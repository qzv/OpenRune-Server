package org.rsmod.content.minigames.colosseum.modifiers

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.PlayerHealCapHook
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statSub
import org.rsmod.content.minigames.colosseum.ColosseumModifiers
import org.rsmod.content.minigames.colosseum.ColosseumRun
import org.rsmod.content.minigames.colosseum.Modifier
import org.rsmod.content.minigames.colosseum.RunState
import org.rsmod.content.minigames.colosseum.runState
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal object FrailtyRules {
    private val REDUCTION_PERCENT = intArrayOf(10, 20, 40)
    private const val PERCENT = 100

    fun cap(level: Int, base: Int): Int? {
        val percent = REDUCTION_PERCENT.getOrNull(level - 1) ?: return null
        return base - base * percent / PERCENT
    }
}

@Singleton
internal class FrailtyModifier @Inject constructor(private val modifiers: ColosseumModifiers) :
    PlayerHealCapHook {
    override fun cap(player: Player): Int? {
        if (player.runState == RunState.None) return null
        return FrailtyRules.cap(modifiers.level(player, Modifier.Frailty), player.baseHitpointsLvl)
    }

    fun onWaveStart(player: Player) {
        val cap = cap(player) ?: return
        val excess = player.hitpoints - cap
        if (excess > 0) player.statSub(HITPOINTS, constant = excess, percent = 0)
    }

    private companion object {
        private const val HITPOINTS = "stat.hitpoints"
    }
}

class FrailtyScript
@Inject
internal constructor(private val run: ColosseumRun, private val frailty: FrailtyModifier) :
    PluginScript() {
    override fun ScriptContext.startup() {
        run.onWaveStart { player, _ -> frailty.onWaveStart(player) }
    }
}

class FrailtyModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerHealCapHook>(FrailtyModifier::class.java)
    }
}
