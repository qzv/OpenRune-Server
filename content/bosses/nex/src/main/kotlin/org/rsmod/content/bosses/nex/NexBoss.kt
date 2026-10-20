package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class NexBoss
@Inject
internal constructor(
    private val deps: BossDeps,
    private val smoke: NexSmoke,
    private val shadow: NexShadow,
    private val blood: NexBlood,
    private val ice: NexIce,
    private val phases: NexPhases,
) : PluginScript() {
    override fun ScriptContext.startup() {
        BossCombat.register(
            this,
            NexSpecs.spec,
            deps,
            onCombatTick = { phases.combatTick(npc) },
            onHit = { phases.onNexHit(this) },
        )
        deps.extensionRegistry.register(NexSpecs.SMOKE_STANDARD) { _, npc, target, _ ->
            smoke.standard(npc, target)
        }
        deps.extensionRegistry.register(NexSpecs.SMOKE_SPECIAL) { _, npc, target, _ ->
            smoke.special(npc, target)
        }
        deps.extensionRegistry.register(NexSpecs.SHADOW_STANDARD) { _, npc, target, _ ->
            shadow.standard(npc, target)
        }
        deps.extensionRegistry.register(NexSpecs.SHADOW_SPECIAL) { _, npc, target, _ ->
            shadow.special(npc, target)
        }
        deps.extensionRegistry.register(NexSpecs.BLOOD_STANDARD) { _, npc, target, _ ->
            blood.standard(npc, target)
        }
        deps.extensionRegistry.register(NexSpecs.BLOOD_SPECIAL) { _, npc, target, _ ->
            blood.special(npc, target)
        }
        deps.extensionRegistry.register(NexSpecs.ICE_STANDARD) { _, npc, target, _ ->
            ice.standard(npc, target)
        }
        deps.extensionRegistry.register(NexSpecs.ICE_SPECIAL) { _, npc, target, _ ->
            ice.special(npc, target)
        }
        onOpLoc1(PRISON_ICICLE) { ice.breakIcicle(player, it.loc) }
        deps.extensionRegistry.register(NexSpecs.MAGE_ATTACK) { _, npc, target, _ ->
            phases.mageAttack(npc, target)
        }
        for ((mage, spec) in NexSpecs.MAGES) {
            BossCombat.register(this, spec, deps)
            val type = ServerCacheManager.getNpc(mage.asRSCM(RSCMType.NPC)) ?: continue
            onNpcQueue(type, DEATH_QUEUE) { with(phases) { mageDeath() } }
        }
        BossCombat.register(this, NexSpecs.REAVER, deps)
        deps.extensionRegistry.register(NexSpecs.REAVER_ATTACK) { _, npc, target, _ ->
            blood.reaverAttack(npc, target)
        }
        val reaver = NexSpecs.REAVER.npcTypes.first()
        ServerCacheManager.getNpc(reaver.asRSCM(RSCMType.NPC))?.let { type ->
            onNpcQueue(type, DEATH_QUEUE) { blood.kill(npc) }
        }
    }

    private companion object {
        private const val DEATH_QUEUE = "queue.death"
        private const val PRISON_ICICLE = "loc.nex_icicle_2"
    }
}
