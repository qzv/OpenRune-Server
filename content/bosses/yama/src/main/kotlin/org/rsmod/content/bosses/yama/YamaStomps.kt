package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.HitmarkTypeGroup
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.setActiveMoveSpeed
import org.rsmod.api.player.vars.varMoveSpeed
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid

@Singleton
internal class YamaStomps
@Inject
constructor(private val deps: BossDeps, private val fights: YamaFights) {
    fun meteor(yama: Npc, player: Player) {
        val fight = fights.of(player) ?: return
        YamaLines.pick(YamaLineGroup.METEOR, deps.random)?.let(yama::say)
        deps.worldQueues.add(CONJURE_DELAY) {
            player.mes(YamaElement.FIRE.colour(YamaText.FIRE_CONJURE))
            val centre = player.coords
            val meteor = spawnMeteor(centre)
            player.abortRoute()
            restrictRun(fight, player, METEOR_IMPACT_DELAY - CONJURE_DELAY)
            fights.disableRunInvert(fight, player, RUN_INVERT_RESTORE - CONJURE_DELAY)
            deps.worldQueues.add(METEOR_IMPACT_DELAY - CONJURE_DELAY) {
                meteorImpact(fight, yama, centre)
            }
            deps.worldQueues.add(METEOR_LIFETIME - CONJURE_DELAY) {
                meteor?.takeIf { it.isSlotAssigned }?.let { deps.npcRepo.del(it, Int.MAX_VALUE) }
            }
        }
    }

    fun shadowStomp(yama: Npc, player: Player) {
        val fight = fights.of(player) ?: return
        YamaLines.pick(YamaLineGroup.SHADOW, deps.random)?.let(yama::say)
        deps.worldQueues.add(CONJURE_DELAY) {
            if (!yama.isSlotAssigned) return@add
            for (target in fight.players.toList()) {
                target.mes(YamaElement.SHADOW.colour(SHADOW_MESSAGE))
                target.soundSynth(SHADOW_LAUNCH_SOUND, delay = SHADOW_LAUNCH_SOUND_DELAY)
                launchShadow(yama, target)
                deps.worldQueues.add(SHADOW_IMPACT_DELAY - CONJURE_DELAY) {
                    shadowImpact(fight, yama, target)
                }
            }
        }
    }

    private fun launchShadow(yama: Npc, target: Player) {
        val type = ServerCacheManager.getProjectile(SHADOW_PROJANIM.asRSCM(RSCMType.PROJANIM))
        checkNotNull(type) { "Projectile not found: $SHADOW_PROJANIM" }
        deps.bossProjectile(
            SHADOW_PROJECTILE.asRSCM(RSCMType.SPOTANIM),
            yama.coords.translate(CENTRE_OFFSET, CENTRE_OFFSET),
            target.coords,
            type.startHeight,
            type.endHeight,
            type.delay,
            type.lengthAdjustment,
            type.angle,
            type.progress,
            homing = target,
        )
    }

    private fun spawnMeteor(centre: CoordGrid): Npc? {
        val type = ServerCacheManager.getNpc(METEOR_NPC.asRSCM(RSCMType.NPC)) ?: return null
        val corner = CoordGrid(centre.x - METEOR_RADIUS, centre.z - METEOR_RADIUS, centre.level)
        val meteor = Npc(type, corner)
        deps.npcRepo.add(meteor, Int.MAX_VALUE)
        meteor.anim(METEOR_SEQ)
        meteor.spotanim(METEOR_SPOTANIM)
        return meteor
    }

    private fun meteorImpact(fight: YamaFight, yama: Npc, centre: CoordGrid) {
        if (!yama.isSlotAssigned) return
        deps.worldRepo.soundArea(
            centre,
            METEOR_IMPACT_SOUND,
            radius = METEOR_SOUND_RADIUS,
            size = METEOR_SOUND_SIZE,
        )
        for (player in fight.players.toList()) {
            val distance = player.coords.chebyshevDistance(centre)
            val max = METEOR_MAX_HITS.getOrElse(distance) { 0 }
            if (max == 0) continue
            if (fight.isImmune(player, YamaElement.FIRE, deps.mapClock.cycle)) {
                absorb(player, yama, YamaElement.FIRE)
                continue
            }
            val damage = if (distance == 0) player.hitpoints else deps.random.of(0, max)
            player.queueHit(yama, 1, HitType.Typeless, damage, deps.playerHitModifier)
            deps.worldQueues.add(BURN_DELAY) {
                if (fight.inJudge) return@add
                player.mes(YamaElement.FIRE.colour(BURN_MESSAGE))
                burn(fight, player, yama, BURN_HITS, fight.phase)
            }
        }
    }

    private fun burn(fight: YamaFight, player: Player, yama: Npc, remaining: Int, phase: Int) {
        val left = fights.of(player) !== fight || fight.phase != phase || fight.inJudge
        val ended = left || !yama.isSlotAssigned || fight.yama?.hitpoints?.let { it <= 0 } == true
        if (remaining <= 0 || ended) {
            player.burnTotal = 0
            player.burnSize = 0
            return
        }
        player.burnTotal = remaining - 1
        player.burnSize = 1
        player.queueHit(yama, 1, HitType.Typeless, 1, deps.playerHitModifier, BURN_HITMARK)
        val next = if (remaining == 1) BURN_CLEAR_DELAY else BURN_INTERVAL
        deps.worldQueues.add(next) { burn(fight, player, yama, remaining - 1, phase) }
    }

    private fun shadowImpact(fight: YamaFight, yama: Npc, player: Player) {
        if (!yama.isSlotAssigned || fights.of(player) !== fight) return
        player.soundSynth(SHADOW_IMPACT_SOUND)
        if (fight.isImmune(player, YamaElement.SHADOW, deps.mapClock.cycle)) {
            absorb(player, yama, YamaElement.SHADOW)
            return
        }
        val damage = deps.random.of(0, SHADOW_MAX_HIT)
        player.finishNpcHit(yama, 1, HitType.Typeless, damage, deps.playerHitModifier)
        player.spotanim(SHADOW_IMPACT_SPOTANIM, slot = SHADOW_IMPACT_SLOT)
        player.mes(BIND_MESSAGE)
        player.statSub("stat.prayer", damage / PRAYER_DRAIN_DIVISOR, 0)
        restrictRun(fight, player, RUN_RESTORE_AFTER_SHADOW)
        fights.disableRunInvert(fight, player, RUN_INVERT_RESTORE - SHADOW_IMPACT_DELAY)
    }

    private fun absorb(player: Player, yama: Npc, element: YamaElement) {
        player.queueHit(
            yama,
            1,
            HitType.Typeless,
            0,
            deps.playerHitModifier,
            YamaStrikes.ABSORBED_HITMARK,
        )
        player.mes(element.absorbMessage(), ChatType.Spam)
    }

    private fun restrictRun(fight: YamaFight, player: Player, ticks: Int) {
        val state = fight.state(player)
        state.noRunUntil = deps.mapClock.cycle + ticks
        if (state.speedBefore == null) state.speedBefore = player.varMoveSpeed
        player.setActiveMoveSpeed(MoveSpeed.Walk)
    }

    private companion object {
        private const val CONJURE_DELAY = 2
        private const val RUN_INVERT_RESTORE = 11
        private const val BURN_CLEAR_DELAY = 1
        private const val METEOR_SOUND_RADIUS = 15
        private const val METEOR_SOUND_SIZE = 15
        private const val SHADOW_LAUNCH_SOUND_DELAY = 6
        private const val METEOR_IMPACT_SOUND = "synth.fireblast_hit"
        private const val SHADOW_LAUNCH_SOUND = "synth.yama_shadow_stomp_launch"
        private const val SHADOW_IMPACT_SOUND = "synth.yama_shadow_stomp_impact"
        private const val METEOR_IMPACT_DELAY = 6
        private const val METEOR_LIFETIME = 10
        private const val SHADOW_IMPACT_DELAY = 5
        private const val RUN_RESTORE_AFTER_SHADOW = 6
        private const val METEOR_RADIUS = 3
        private const val BURN_DELAY = 1
        private const val BURN_INTERVAL = 4
        private const val BURN_HITS = 11
        private const val SHADOW_MAX_HIT = 60
        private const val PRAYER_DRAIN_DIVISOR = 3
        private const val SHADOW_IMPACT_SLOT = 2

        private const val METEOR_NPC = "npc.yama_meteor_npc"
        private const val METEOR_SEQ = "seq.vfx_yama_meteor_impact_02"
        private const val METEOR_SPOTANIM = "spotanim.vfx_yama_meteor_projectile_02"
        private const val CENTRE_OFFSET = 2
        private const val SHADOW_PROJECTILE = "spotanim.vfx_yama_shadow_root_projectile_01"
        private const val SHADOW_PROJANIM = "projanim.yama_shadow_stomp"
        private const val SHADOW_IMPACT_SPOTANIM = "spotanim.vfx_yama_shadow_root_impact_01"
        private const val SHADOW_MESSAGE = "Yama conjures a shadow-based attack at you."
        private const val BURN_MESSAGE = "You have been set alight!"
        private const val BIND_MESSAGE = "Yama briefly binds you!"
        private val BURN_HITMARK = HitmarkTypeGroup("hitmark.burn")
        private val METEOR_MAX_HITS = intArrayOf(80, 80, 80, 40)
    }
}
