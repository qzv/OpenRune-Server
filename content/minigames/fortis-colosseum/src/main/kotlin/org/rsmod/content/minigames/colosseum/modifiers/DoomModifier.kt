package org.rsmod.content.minigames.colosseum.modifiers

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.minigames.colosseum.ColosseumModifiers
import org.rsmod.content.minigames.colosseum.Modifier
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hitmark

@Singleton
internal class DoomModifier @Inject constructor(private val modifiers: ColosseumModifiers) {
    private val doomHitmark by lazy { hitmark_groups.doom.lit.asRSCM(RSCMType.HITMARK) }

    fun onDamage(player: Player) {
        val level = modifiers.level(player, Modifier.Doom)
        if (level <= 0 || DEATH_QUEUE in player.queueList) return
        val limit = DoomRules.limit(level)
        val stacks = player.vars[STACKS_VARBIT]
        if (stacks >= limit) {
            doom(player)
            return
        }
        val next = stacks + 1
        player.mes("You now have <col=ff3045>$next/$limit points</col> of <col=ff3045>doom!</col>")
        VarPlayerIntMapSetter.set(player, STACKS_VARBIT, next)
        val stackMark = Hitmark.fromNoSource(doomHitmark, doomHitmark, doomHitmark, next, 0)
        player.activeHitmarks.add(
            (player.activeHitmarks.size - 1).coerceAtLeast(0),
            stackMark.packed,
        )
        player.spotanim(
            STACK_SPOTANIM,
            delay = STACK_SPOTANIM_DELAY,
            height = STACK_SPOTANIM_HEIGHT,
        )
        player.soundSynth(STACK_SYNTH)
    }

    fun onWaveStart(player: Player) {
        if (player.vars[STACKS_VARBIT] <= 0) return
        reset(player)
        player.mes("<col=229628>Your Doom points have reset to 0.</col>")
    }

    fun reset(player: Player) {
        if (player.vars[STACKS_VARBIT] != 0) VarPlayerIntMapSetter.set(player, STACKS_VARBIT, 0)
    }

    private fun doom(player: Player) {
        player.mes("<col=ff3045>You have been doomed!</col>")
        player.spotanim(DEATH_SPOTANIM, slot = DEATH_SPOTANIM_SLOT)
        player.anim(DEATH_SEQ)
        player.strongQueue(DEATH_QUEUE, DOOM_DEATH_DELAY)
    }

    private companion object {
        private const val STACK_SYNTH = "synth.colosseum_doom_stack"
        private const val STACKS_VARBIT = "varbit.colosseum_doom_stacks_client"
        private const val DEATH_QUEUE = "queue.death"
        private const val STACK_SPOTANIM = "spotanim.vfx_doom_scorpion_attack_impact_01"
        private const val STACK_SPOTANIM_DELAY = 1
        private const val STACK_SPOTANIM_HEIGHT = 60
        private const val DEATH_SPOTANIM = "spotanim.vfx_doom_scorpion_01_player_death_01"
        private const val DEATH_SPOTANIM_SLOT = 1
        private const val DEATH_SEQ = "seq.human_doom_scorpion_01_player_death_01"
        private const val DOOM_DEATH_DELAY = 9
    }
}
