package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class YamaBoss
@Inject
internal constructor(
    private val deps: BossDeps,
    private val fights: YamaFights,
    private val judge: YamaJudge,
    private val stomps: YamaStomps,
    private val flares: YamaVoidFlares,
    private val finale: YamaPhaseThree,
    private val death: YamaDeath,
    private val strikes: YamaStrikes,
) : PluginScript() {
    override fun ScriptContext.startup() {
        BossCombat.register(this, YamaSpecs.all, deps)
        judge.registerHandlers(this)
        flares.registerHandlers(this)
        death.registerHandlers(this)
        deps.extensionRegistry.register(METEOR) { _, npc, target, _ ->
            fights.attackStarted(target)
            stomps.meteor(npc, target)
        }
        deps.extensionRegistry.register(SHADOW_STOMP) { _, npc, target, _ ->
            fights.attackStarted(target)
            stomps.shadowStomp(npc, target)
        }
        deps.extensionRegistry.register(FLARE_CAST) { _, _, target, _ ->
            fights.attackStarted(target)
            fights.of(target)?.let { flares.spawnEarly(it) }
        }
        deps.extensionRegistry.register(PHASE_THREE) { _, npc, target, _ ->
            val index = fights.attackStarted(target)
            finale.attack(npc, target, index)
        }
        deps.extensionRegistry.register(JUDGE) { _, npc, target, params ->
            judge.begin(npc, target, params as String)
        }
        deps.extensionRegistry.register(STANDARD) { _, npc, target, params ->
            standard(npc, target, params as YamaElement)
        }
        deps.extensionRegistry.register(SPECIAL_START) { _, npc, target, params ->
            specialStart(npc, target, params as YamaElement)
        }
    }

    private fun standard(npc: Npc, target: Player, element: YamaElement) {
        fights.attackStarted(target)
        if (strikes.canMelee(npc, target)) {
            strikes.melee(npc, target)
            return
        }
        strikes.standard(npc, target, element)
        fights.acquisitionCrash(target)
    }

    private fun specialStart(npc: Npc, target: Player, element: YamaElement) {
        fights.attackStarted(target)
        val fight = fights.of(target)
        val contract = fight?.contract != null
        val delay = if (contract) CONTRACT_SPECIAL_IMPACT_DELAY else SPECIAL_IMPACT_DELAY
        val text = if (element == YamaElement.FIRE) YamaText.FIRE_CONJURE else SHADOW_LAUNCH
        val players = fight?.players?.toList() ?: listOf(target)
        val spotanim = YamaSpecs.specialProjectileSpotanim(element).asRSCM(RSCMType.SPOTANIM)
        val projanim = YamaSpecs.specialProjectile(contract).asRSCM(RSCMType.PROJANIM)
        val type = checkNotNull(ServerCacheManager.getProjectile(projanim))
        for (player in players) {
            if (player !== target) {
                deps.worldRepo.projAnim(ProjAnim.fromNpcToPlayer(npc, player, spotanim, type))
            }
            deps.worldQueues.add(delay) { specialImpact(npc, player, element) }
            player.mes(element.colour(text))
        }
    }

    private fun specialImpact(npc: Npc, target: Player, element: YamaElement) {
        if (fights.of(target)?.yama !== npc || npc.hitpoints <= 0) return
        if (strikes.absorbed(target, element)) {
            target.queueHit(
                npc,
                1,
                HitType.Typeless,
                0,
                deps.playerHitModifier,
                YamaStrikes.ABSORBED_HITMARK,
            )
            return
        }
        val damage = deps.random.of(0, UNPROTECTED_SPECIAL_MAX_HIT)
        target.finishNpcHit(npc, 1, HitType.Typeless, damage, deps.playerHitModifier)
    }

    internal companion object {
        const val STANDARD = "yama.standard"
        const val SPECIAL_START = "yama.special_start"
        const val JUDGE = "yama.judge"
        const val FLARE_CAST = "yama.flare_cast"
        const val PHASE_THREE = "yama.phase_three"
        const val METEOR = "yama.meteor"
        const val SHADOW_STOMP = "yama.shadow_stomp"
        const val UNPROTECTED_SPECIAL_MAX_HIT = 46
        private const val SPECIAL_IMPACT_DELAY = 6
        private const val CONTRACT_SPECIAL_IMPACT_DELAY = 5
        private const val SHADOW_LAUNCH = "Yama launches a shadow-based attack at you."
    }
}
