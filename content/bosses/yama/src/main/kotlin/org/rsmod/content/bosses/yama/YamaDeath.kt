package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.table.YamaContractDropsRow
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

internal object YamaContractRewards {
    fun items(contract: YamaContract, chosenPiece: String?): List<ContractReward> =
        buildList {
            addAll(contract.rewards)
            chosenPiece?.let { add(ContractReward(it, 1)) }
            if (contract.returned) add(ContractReward(contract.item, 1))
        }
}

@Singleton
internal class YamaDeath
@Inject
constructor(
    private val deps: BossDeps,
    private val manager: InstanceManager,
    private val fights: YamaFights,
    private val npcDeath: NpcDeath,
    private val bossHpBar: BossHpBarScript,
    private val objRepo: ObjRepository,
    private val firewall: YamaFirewall,
    private val dropRegistry: DropTableRegistry,
    private val contractOverlay: YamaContractOverlay,
    private val music: YamaMusic,
) {
    private val contractDrops: Map<String, Boolean> by lazy {
        YamaContractDropsRow.all().associate {
            RSCM.getReverseMapping(RSCMType.OBJ, it.obj.id) to it.unique
        }
    }

    fun registerHandlers(ctx: ScriptContext) {
        with(ctx) {
            val type =
                ServerCacheManager.getNpc(YamaSpecs.YAMA_NPC.asRSCM(RSCMType.NPC)) ?: return
            onNpcQueue(type, "queue.death") { die() }
        }
    }

    private suspend fun StandardNpcAccess.die() {
        val fight = fights.byYama(npc)
        val players = fight?.players?.toList().orEmpty()
        val deathTick = deps.mapClock.cycle
        fight?.let { manager.sessionForPlayer(it.creator) }?.let(firewall::lower)
        noneMode()
        hideAllOps()
        anim(DESPAWN_SEQ)
        spotanim(DESPAWN_SPOTANIM)
        if (fight != null) fights.finish(fight)
        delay(RESULT_DELAY)
        if (fight != null) {
            for (player in players) {
                player.throneState = 0
                contractOverlay.close(player)
                music.play(player)
                fight.contract?.let { player.mes("<col=3366ff>${it.title}, fulfilled.</col>") }
                val kills = player.totalYamaKills + 1
                player.totalYamaKills = kills
                player.mes("Your Yama success count is: <col=ff0000>$kills</col>.")
                reportDuration(player, deathTick - fight.startTick)
            }
            fight.throne?.let {
                it.hideAllOps()
                it.anim(THRONE_RESPAWN_SEQ)
                it.spotanim(THRONE_RESPAWN_SPOTANIM)
            }
        }
        val access = this
        val yama = npc
        deps.worldQueues.add(SAY_DELAY - RESULT_DELAY) {
            val line = YamaLines.pick(YamaLineGroup.THRONE, deps.random)
            if (line != null) fight?.throne?.say(line)
        }
        deps.worldQueues.add(THRONE_OPS_DELAY - RESULT_DELAY) { fight?.throne?.showAllOps() }
        deps.worldQueues.add(LOOT_DELAY - RESULT_DELAY) {
            if (fight != null && players.isNotEmpty()) dropLoot(access, yama, fight, players)
        }
        deps.worldQueues.add(BAR_CLOSE_DELAY - RESULT_DELAY) {
            players.forEach { bossHpBar.onClose(it, access.npc) }
        }
        delay(REMOVE_DELAY - RESULT_DELAY)
        deps.npcRepo.del(npc, Int.MAX_VALUE)
    }

    private fun dropLoot(
        access: StandardNpcAccess,
        yama: Npc,
        fight: YamaFight,
        players: List<Player>,
    ) {
        val session = manager.sessionForPlayer(fight.creator)
        val drop = session?.let { manager.resolveCoord(it, LOOT_TILE) } ?: access.coords
        val contract = fight.contract
        if (contract == null && players.size == 1) {
            npcDeath.spawnDrops(access, drop)
            return
        }
        for (player in players) {
            val damage = yama.damageContributions.damageBy(player)
            val share =
                if (players.size == 1) 1.0 else YamaLootShare.of(damage, yama.baseHitpointsLvl)
            val duration = player.lootDropDuration ?: constants.lootdrop_duration
            if (contract == null) {
                rollTable(player, share, drop, duration) { true }
            } else {
                contractRewards(contract, fight, drop, player, share)
            }
        }
    }

    private fun reportDuration(player: Player, ticks: Int) {
        val time = formatTime(ticks)
        val best = player.yamaBestTime
        if (best == 0 || ticks < best) {
            player.yamaBestTime = ticks
            player.mes("Fight duration: <col=ff0000>$time</col> (new personal best)")
            return
        }
        player.mes("Fight duration: <col=ff0000>$time</col>. Personal best: ${formatTime(best)}")
    }

    private fun formatTime(ticks: Int): String {
        val seconds = ticks * SECONDS_PER_TICK_TENTHS / TENTHS
        return "${seconds / SECONDS_PER_MINUTE}:${"%02d".format(seconds % SECONDS_PER_MINUTE)}"
    }

    private fun contractRewards(
        contract: YamaContract,
        fight: YamaFight,
        drop: CoordGrid,
        player: Player,
        share: Double,
    ) {
        val duration = player.lootDropDuration ?: constants.lootdrop_duration
        if (player === fight.contractHolder) {
            for (reward in YamaContractRewards.items(contract, fight.chosenPiece)) {
                objRepo.add(reward.item, drop, duration, player, reward.count)
            }
        }
        val fullTable = contract.keepsFullTable
        rollTable(player, share, drop, duration) {
            if (fullTable) it != PET else it in contractDrops
        }
        val petRate = contract.petRate ?: return
        if (deps.random.of(petRate) == 0) objRepo.add(PET, drop, duration, player, 1)
    }

    private fun rollTable(
        player: Player,
        share: Double,
        drop: CoordGrid,
        duration: Int,
        keep: (String) -> Boolean,
    ) {
        val table = dropRegistry.forNpc(YamaSpecs.YAMA_NPC) ?: return
        for (result in table.roll(player, ArgMap()).flatten().results()) {
            if (result.isNothing || !result.condition(player) || !keep(result.obj)) continue
            val unique = contractDrops[result.obj] == true
            if (unique && !keepsUnique(share)) continue
            val count = result.rollCount(deps.random)
            val scaled = if (unique) count else YamaLootShare.quantity(count, share)
            val obj = result.transformObj(player) ?: result.obj
            objRepo.add(obj, drop, duration, player, scaled)
        }
    }

    private fun keepsUnique(share: Double): Boolean =
        YamaLootShare.keepsUnique(share, deps.random.of(YamaLootShare.SCALE))

    private fun RollResult<DropRollItem>.results(): List<DropRollItem> =
        when (this) {
            is RollResult.Nothing -> emptyList()
            is RollResult.Single -> listOf(result)
            is RollResult.ListOf -> results
        }

    private companion object {
        private const val DESPAWN_SEQ = "seq.npc_yama01_despawn01"
        private const val DESPAWN_SPOTANIM = "spotanim.vfx_yama_combat_despawn_spotanim01"
        private const val THRONE_RESPAWN_SEQ = "seq.npc_yama_noncombat_spawn02"
        private const val THRONE_RESPAWN_SPOTANIM = "spotanim.vfx_yama_noncombat_spawn_spotanim02"
        private const val RESULT_DELAY = 5
        private const val REMOVE_DELAY = 7
        private const val SAY_DELAY = 9
        private const val LOOT_DELAY = 11
        private const val THRONE_OPS_DELAY = 11
        private const val BAR_CLOSE_DELAY = 15
        private const val SECONDS_PER_TICK_TENTHS = 6
        private const val TENTHS = 10
        private const val SECONDS_PER_MINUTE = 60
        private const val PET = "obj.yamapet"
        private val LOOT_TILE = CoordGrid(1503, 10088)
    }
}
