package org.rsmod.content.bosses.yama

import dev.openrune.types.HitmarkTypeGroup
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType

@Singleton
internal class YamaStrikes
@Inject
constructor(private val deps: BossDeps, private val fights: YamaFights) {
    fun canMelee(yama: Npc, player: Player): Boolean {
        val dx = player.coords.x - yama.coords.x
        val dz = player.coords.z - yama.coords.z
        val size = yama.size
        return dx in -1..size && dz in -1..size && !(dx in 0 until size && dz in 0 until size)
    }

    fun melee(yama: Npc, player: Player) {
        val fight = fights.of(player)
        yama.anim(MELEE_SEQ)
        val victims =
            fight?.players?.filter {
                it.coords.chebyshevDistance(player.coords) <= MELEE_SPLASH_RADIUS
            } ?: listOf(player)
        for (victim in victims) meleeHit(yama, victim, fight)
    }

    private fun meleeHit(yama: Npc, player: Player, fight: YamaFight?) {
        if (!yama.isSlotAssigned) return
        if (player.vars[PROTECT_MELEE] == 1) {
            val pierce =
                if (fight?.phase == YamaPhases.FINAL) FINAL_MELEE_PIERCE else EARLY_MELEE_PIERCE
            val damage = deps.random.of(0, pierce)
            player.finishNpcHit(
                yama,
                MELEE_HIT_DELAY,
                HitType.Typeless,
                damage,
                deps.playerHitModifier,
            )
            return
        }
        val accuracy = deps.accuracy
        val landed = accuracy.rollMeleeAccuracy(yama, player, MeleeAttackType.Slash, deps.random)
        val damage = if (landed) deps.random.of(0, maxHit(fight)) else 0
        player.finishNpcHit(yama, MELEE_HIT_DELAY, HitType.Melee, damage, deps.playerHitModifier)
    }

    fun standard(yama: Npc, player: Player, element: YamaElement) {
        val fire = element == YamaElement.FIRE
        yama.anim(STANDARD_SEQ, delay = STANDARD_CAST_DELAY)
        yama.spotanim(if (fire) FIRE_SPOTANIM else SHADOW_SPOTANIM, delay = STANDARD_CAST_DELAY)
        player.spotanim(if (fire) FIRE_IMPACT else SHADOW_IMPACT, slot = IMPACT_SLOT)
        deps.worldQueues.add(STANDARD_HIT_DELAY) { landStandard(yama, player, element) }
    }

    private fun landStandard(yama: Npc, player: Player, element: YamaElement) {
        val fight = fights.of(player)
        if (!yama.isSlotAssigned || fight?.yama !== yama) return
        val type = if (element == YamaElement.FIRE) HitType.Magic else HitType.Ranged
        if (absorbed(player, element)) {
            player.finishNpcHit(yama, 1, type, 0, deps.playerHitModifier)
            return
        }
        val contract = fight?.contract
        val protected = protectedFrom(player, type)
        val landed = rollAccuracy(yama, player, type)
        if (landed) deps.worldQueues.add(POISON_DELAY) { poison(player) }
        when {
            protected && contract?.prayerPierce == true -> {
                val damage = deps.random.of(0, PIERCE_MAX_HIT)
                player.finishNpcHit(yama, 1, HitType.Typeless, damage, deps.playerHitModifier)
            }
            protected && contract == null && fight?.phase == YamaPhases.FINAL -> {
                val damage = deps.random.of(0, FINAL_PROTECTED_MAX_HIT)
                player.finishNpcHit(yama, 1, HitType.Typeless, damage, deps.playerHitModifier)
            }
            else ->
                player.finishNpcHit(
                    yama,
                    1,
                    type,
                    if (landed) deps.random.of(0, maxHit(fight)) else 0,
                    deps.playerHitModifier,
                    penetration = contract?.penetration ?: 0,
                )
        }
    }

    fun absorbed(player: Player, element: YamaElement): Boolean {
        val fight = fights.of(player) ?: return false
        if (!fight.isImmune(player, element, deps.mapClock.cycle)) return false
        player.mes(element.absorbMessage(), ChatType.Spam)
        return true
    }

    private fun poison(player: Player) {
        if (player.vars[POISON_SEVERITY] > 0) return
        CombatEffects.poison(player, POISON_DAMAGE)
    }

    private fun rollAccuracy(yama: Npc, player: Player, type: HitType): Boolean =
        if (type == HitType.Magic) {
            deps.accuracy.rollMagicAccuracy(yama, player, deps.random)
        } else {
            deps.accuracy.rollRangedAccuracy(yama, player, deps.random)
        }

    private fun maxHit(fight: YamaFight?): Int =
        if (fight?.contract != null) CONTRACT_MAX_HIT else BASE_MAX_HIT

    private fun protectedFrom(player: Player, type: HitType): Boolean =
        player.vars[if (type == HitType.Magic) PROTECT_MAGIC else PROTECT_MISSILES] == 1

    companion object {
        val ABSORBED_HITMARK = HitmarkTypeGroup(lit = "hitmark.shield_damage_max")
        const val STANDARD_SEQ = "seq.npc_yama01_magic01"
        const val MELEE_SEQ = "seq.npc_yama01_melee01"
        const val FIRE_SPOTANIM = "spotanim.vfx_npc_yama_magic_fire_spotanim01"
        const val SHADOW_SPOTANIM = "spotanim.vfx_npc_yama_magic_shadow_spotanim01"
        private const val FIRE_IMPACT = "spotanim.vfx_player_yama_magic_fire_impact01"
        private const val SHADOW_IMPACT = "spotanim.vfx_player_yama_magic_shadow_impact01"
        private const val IMPACT_SLOT = 2
        private const val STANDARD_CAST_DELAY = 30
        private const val STANDARD_HIT_DELAY = 3
        private const val MELEE_HIT_DELAY = 2
        private const val MELEE_SPLASH_RADIUS = 3
        private const val BASE_MAX_HIT = 46
        private const val CONTRACT_MAX_HIT = 54
        private const val EARLY_MELEE_PIERCE = 6
        private const val FINAL_MELEE_PIERCE = 22
        private const val FINAL_PROTECTED_MAX_HIT = 2
        private const val PIERCE_MAX_HIT = 8
        private const val POISON_DAMAGE = 20
        private const val POISON_DELAY = 1
        private const val POISON_SEVERITY = "varp.poison_severity"
        private const val PROTECT_MAGIC = "varbit.prayer_protectfrommagic"
        private const val PROTECT_MISSILES = "varbit.prayer_protectfrommissiles"
        private const val PROTECT_MELEE = "varbit.prayer_protectfrommelee"
    }
}
