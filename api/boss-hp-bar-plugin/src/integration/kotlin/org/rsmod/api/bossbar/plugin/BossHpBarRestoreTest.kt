package org.rsmod.api.bossbar.plugin

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import dev.openrune.types.util.ParamMapBuilder
import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.varp.VarpLarge
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import org.junit.jupiter.api.Test
import org.rsmod.api.bossbar.BossHpBarMode
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.hit.NpcHitpointsRestorer
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

class BossHpBarRestoreTest {
    @Test
    fun GameTestState.`a heal resends the bar hitpoints with the next update`() =
        runInjectedGameTest(BarDeps::class, null, BossHpBarScript::class) { deps ->
            val boss = spawnNpc(BOSS_COORDS, bossType())
            deps.damage(boss, player, HIT)
            val bar = BarRecorder(this)
            bar.advance()

            deps.restorer.heal(boss, HEAL, showHitsplat = true)
            bar.advance()

            assertEquals(listOf(BOSS_HITPOINTS - HIT, BOSS_HITPOINTS - HIT + HEAL), bar.changes())
        }

    @Test
    fun GameTestState.`a regen restore resends the bar hitpoints with the next update`() =
        runInjectedGameTest(BarDeps::class, null, BossHpBarScript::class) { deps ->
            val boss = spawnNpc(BOSS_COORDS, bossType())
            deps.damage(boss, player, HIT)
            val bar = BarRecorder(this)
            bar.advance()

            boss.hitpoints++
            deps.restorer.restored(boss)
            bar.advance()

            assertEquals(listOf(BOSS_HITPOINTS - HIT, BOSS_HITPOINTS - HIT + 1), bar.changes())
        }

    @Test
    fun GameTestState.`a heal of an npc whose bar is not shown sends nothing`() =
        runInjectedGameTest(BarDeps::class, null, BossHpBarScript::class) { deps ->
            val boss = spawnNpc(BOSS_COORDS, bossType())
            boss.hitpoints -= HIT
            val bar = BarRecorder(this)

            deps.restorer.heal(boss, HEAL)
            bar.advance()

            assertEquals(emptyList<Int>(), bar.changes())
        }

    private class BarDeps
    @Inject
    constructor(
        private val contributor: BossHpBarDamageContributor,
        restoreHook: BossHpBarRestoreHook,
    ) {
        val restorer = NpcHitpointsRestorer(setOf(restoreHook))

        fun damage(npc: Npc, source: Player, amount: Int) {
            npc.hitpoints -= amount
            contributor.onPlayerDamageNpc(npc, source, amount)
        }
    }

    private class BarRecorder(private val scope: GameTestScope) {
        private val varbit =
            checkNotNull(ServerCacheManager.getVarbit(BAR_HITPOINTS_VARBIT.asRSCM(RSCMType.VARBIT)))
        private val mask = (1 shl (varbit.endBit - varbit.startBit + 1)) - 1

        private val sent = mutableListOf<Int>()

        fun advance() {
            scope.advance()
            for (message in scope.client.outgoingMessages) {
                val (varp, value) =
                    when (message) {
                        is VarpSmall -> message.id to message.value
                        is VarpLarge -> message.id to message.value
                        else -> continue
                    }
                if (varp == varbit.varp) sent += (value ushr varbit.startBit) and mask
            }
        }

        fun changes(): List<Int> = sent.filterIndexed { i, value -> i == 0 || value != sent[i - 1] }
    }

    private companion object {
        val BOSS_COORDS = CoordGrid(0, 50, 50, 1, 1)
        const val BAR_HITPOINTS_VARBIT = "varbit.hpbar_hud_hp"
        const val BOSS_HITPOINTS = 500
        const val HIT = 50
        const val HEAL = 20

        fun bossType(): NpcServerType =
            npcTypeFactory.create {
                hitpoints = BOSS_HITPOINTS
                regenRate = 0
                paramMap =
                    ParamMapBuilder()
                        .apply { this[params.boss_hp_bar_mode] = BossHpBarMode.ON_ATTACK.id }
                        .toParamMap()
            }
    }
}
