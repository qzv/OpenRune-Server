package org.rsmod.content.minigames.colosseum.modifiers

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.HitmarkTypeGroup
import jakarta.inject.Inject
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onPlayerHit
import org.rsmod.content.minigames.colosseum.ColosseumRun
import org.rsmod.content.minigames.colosseum.ColosseumWaves
import org.rsmod.content.minigames.colosseum.RunState
import org.rsmod.content.minigames.colosseum.runState
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

class ColosseumModifierEffects
@Inject
internal constructor(
    private val run: ColosseumRun,
    private val waves: ColosseumWaves,
    private val doom: DoomModifier,
    private val blasphemy: BlasphemyModifier,
    private val bees: BeesModifier,
    private val volatility: VolatilityModifier,
    private val totemic: TotemicModifier,
    private val solarflare: SolarflareModifier,
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    private val damageHitmarks by lazy { hitmarkIds(hitmark_groups.regular_damage) }

    override fun ScriptContext.startup() {
        onPlayerHit {
            if (player.runState == RunState.None || waves.stateOf(player) == null)
                return@onPlayerHit
            if (solarflare.isFlareHit(hit) || !isEnemyDamage(hit)) return@onPlayerHit
            doom.onDamage(player)
            if (hit.type != HitType.Typeless) blasphemy.onHit(player, hit)
        }
        run.beforeWaveStart { player -> doom.onWaveStart(player) }
        run.onWaveStart { player, state ->
            bees.onWaveStart(player, state)
            solarflare.onWaveStart(player, state)
        }
        run.onSolSpawn { player, state, _ ->
            bees.onSolSpawn(player, state)
            solarflare.onSolSpawn(player, state)
        }
        run.onWaveEnd { _, state ->
            bees.onWaveEnd(state)
            totemic.onWaveEnd(state)
            solarflare.onWaveEnd()
        }
        waves.onOpen { state -> playerOf(state.playerUuid)?.let(doom::reset) }
        waves.onClose { state ->
            bees.onClose(state)
            totemic.onWaveEnd(state)
            solarflare.onWaveEnd()
            playerOf(state.playerUuid)?.let(doom::reset)
        }
        waves.onEnemyDespawn(volatility::onEnemyDespawn)
        registerBeeDeath()
        registerTotemDeath()
    }

    private fun ScriptContext.registerBeeDeath() {
        val type = checkNotNull(ServerCacheManager.getNpc(BeesModifier.BEES.asRSCM(RSCMType.NPC)))
        onNpcQueue(type, DEATH_QUEUE) {
            val bee = bees.of(npc)
            if (bee == null) {
                npcRepo.del(npc, Int.MAX_VALUE)
                return@onNpcQueue
            }
            bees.onKilled(bee)
            hideAllOps()
            anim(BeesModifier.DESPAWN_SEQ)
            delay(BEE_HIDE_DELAY)
            if (!npc.isSlotAssigned) return@onNpcQueue
            teleport(bee.home, collision)
            resetAnim()
            npc.removeHeadbar(DEFAULT_HEADBAR.asRSCM(RSCMType.HEADBAR))
            bees.onHidden(bee)
        }
    }

    private fun ScriptContext.registerTotemDeath() {
        val type =
            checkNotNull(ServerCacheManager.getNpc(TotemicModifier.TOTEM.asRSCM(RSCMType.NPC)))
        onNpcQueue(type, DEATH_QUEUE) {
            totemic.onTotemDestroyed(npc)
            delay(TOTEM_DELETE_DELAY)
            if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    private fun isEnemyDamage(hit: Hit): Boolean =
        hit.damage > 0 && hit.hitmark.self in damageHitmarks

    private fun playerOf(uuid: Long?) = uuid?.let { id -> playerList.firstOrNull { it.uuid == id } }

    private fun hitmarkIds(group: HitmarkTypeGroup): Set<Int> =
        listOfNotNull(group.lit, group.tint, group.max).map { it.asRSCM(RSCMType.HITMARK) }.toSet()

    private companion object {
        private const val DEATH_QUEUE = "queue.death"
        private const val BEE_HIDE_DELAY = 3
        private const val TOTEM_DELETE_DELAY = 1
        private const val DEFAULT_HEADBAR = "headbar.health_30"
    }
}
