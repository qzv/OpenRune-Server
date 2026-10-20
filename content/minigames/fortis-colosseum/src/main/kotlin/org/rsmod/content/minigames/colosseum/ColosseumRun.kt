package org.rsmod.content.minigames.colosseum

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.table.MusicRow
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.OpVisibility
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.queue.WorldQueueList

@Singleton
internal class ColosseumRun
@Inject
constructor(
    private val waves: ColosseumWaves,
    private val modifiers: ColosseumModifiers,
    private val rewards: ColosseumRewards,
    private val worldQueues: WorldQueueList,
    private val locRepo: LocRepository,
    private val sand: ColosseumSand,
    private val music: MusicPlayer,
) {
    private val waveStartHooks = mutableListOf<(Player, WaveState) -> Unit>()
    private val preWaveStartHooks = mutableListOf<(Player) -> Unit>()
    private val waveEndHooks = mutableListOf<(Player, WaveState) -> Unit>()
    private val solSpawnHooks = mutableListOf<(Player, WaveState, Npc) -> Unit>()

    private val arenaTrack: MusicRow by lazy {
        MusicRow.all().first { it.displayname == ARENA_TRACK_NAME }
    }

    var solStarter: (suspend ProtectedAccess.() -> Unit)? = null

    fun beforeWaveStart(hook: (Player) -> Unit) {
        preWaveStartHooks += hook
    }

    fun onWaveStart(hook: (Player, WaveState) -> Unit) {
        waveStartHooks += hook
    }

    fun onWaveEnd(hook: (Player, WaveState) -> Unit) {
        waveEndHooks += hook
    }

    fun onSolSpawn(hook: (Player, WaveState, Npc) -> Unit) {
        solSpawnHooks += hook
    }

    fun solSpawned(player: Player, state: WaveState, sol: Npc) {
        solSpawnHooks.forEach { it(player, state, sol) }
    }

    fun reset(player: Player) {
        player.runWave = 0
        player.runState = RunState.Entering
        player.currentGlory = 0
        player.waveStartTime = 0
        player.lastWaveDuration = 0
        player.totalDuration = 0
        player.waveDamageTaken = 0
        modifiers.reset(player)
        rewards.reset(player)
        rewards.preRoll(player, 1)
        modifiers.rollOffers(player, 1)
    }

    private fun announceRank(player: Player) {
        val title = ColosseumRank.title(player.bestGlory)
        player.mes(
            "<col=229628>Congratulations, you've reached $title status inside the Colosseum!"
        )
        player.mes(
            "<col=229628>Check the board in the Colosseum lobby to see any benefits you may " +
                "have unlocked!"
        )
    }

    fun commitGlory(player: Player) {
        if (player.currentGlory <= player.bestGlory) return
        val previousRank = ColosseumRank.index(player.bestGlory)
        player.bestGlory = player.currentGlory
        if (ColosseumRank.index(player.bestGlory) > previousRank) announceRank(player)
    }

    fun clear(player: Player) {
        if (player.runState != RunState.Leaving) {
            commitGlory(player)
            rewards.stopTransmit(player)
        }
        rewards.setLegacyValue(player, rewards.value(rewards.earned(player)))
        player.runState = RunState.None
        player.runWave = 0
        rewards.reset(player)
    }

    fun confirmModifier(player: Player): Boolean {
        val state = waves.stateOf(player) ?: return false
        if (player.runState != RunState.AwaitingStart && player.runState != RunState.Intermission)
            return false
        val modifier = modifiers.offered(player, player.selectedModifier) ?: return false
        modifiers.apply(player, modifier)
        player.waveDamageTaken = 0
        player.runState = RunState.WaveActive
        waves.despawnMinimus(state)
        player.queue(WAVE_START_QUEUE, WAVE_START_DELAY + 1)
        return true
    }

    fun startWave(player: Player): Boolean {
        val state = waves.stateOf(player) ?: return false
        player.preventLogoutMessage = ColosseumLogoutHook.WAVE_START_MESSAGE
        player.preventLogoutUntil = waves.now()
        val wave = player.runWave + 1
        player.waveStartTime = waves.now()
        if (wave > player.highestWave) player.highestWave = wave
        preWaveStartHooks.forEach { it(player) }
        music.play(player, arenaTrack)
        if (wave == ColosseumModifiers.SOL_WAVE) {
            state.wave = wave
            waveStartHooks.forEach { it(player, state) }
            return true
        }
        player.mes("<col=ff3045>Wave: $wave</col>")
        waves.spawnWave(player, state, wave)
        waveStartHooks.forEach { it(player, state) }
        val started = player.waveStartTime
        worldQueues.add(REINFORCEMENT_DELAY) {
            val current = waves.stateOf(player) ?: return@add
            if (
                current !== state ||
                    player.waveStartTime != started ||
                    player.runState != RunState.WaveActive
            ) {
                return@add
            }
            waves.reinforce(player, state)
        }
        return false
    }

    fun onEnemyRemoved(state: WaveState, npc: Npc) {
        val player = waves.onEnemyRemoved(state, npc) ?: return
        if (player.runState != RunState.WaveActive) return
        completeWave(player, state)
    }

    fun completeWave(player: Player, state: WaveState) {
        val wave = player.runWave + 1
        val duration = waves.now() - player.waveStartTime
        player.runWave = wave
        player.totalWavesCompleted += 1
        player.lastWaveDuration = duration
        player.totalDuration += duration
        val gained =
            ColosseumGlory.earned(
                wave,
                duration,
                player.waveDamageTaken,
                modifiers.ownedGlory(player),
            )
        player.currentGlory += gained
        waves.clearExtras(state)
        if (wave != ColosseumModifiers.SOL_WAVE) sand.clearTemporary(state)
        waveEndHooks.forEach { it(player, state) }
        worldQueues.add(TOXIN_CLEAR_DELAY) {
            PlayerVenom.clear(player)
            PlayerPoison.clear(player)
        }
        val shown = if (wave == ColosseumModifiers.SOL_WAVE) player.totalDuration else duration
        player.mes(
            "Wave $wave completed! Wave duration: " +
                "<col=ff3045>${ColosseumGlory.formatDuration(player, shown)}</col>"
        )
        rewards.bankWave(player)
        if (wave >= ColosseumModifiers.SOL_WAVE) {
            player.runState = RunState.Finished
            return
        }
        rewards.preRoll(player, wave + 1)
        player.runState = RunState.Intermission
        player.queue(INTERMISSION_QUEUE, INTERMISSION_DELAY + 1)
    }

    fun finish(player: Player, chestDelay: Int) {
        val state = waves.stateOf(player) ?: return
        player.runState = RunState.Finished
        player.midiSong(STOP_MUSIC, fadeOutSpeed = STOP_MUSIC_FADE)
        player.mes("<col=229628>Search the chest nearby to retrieve your earned rewards!")
        rewards.setLegacyValue(player, rewards.value(rewards.earned(player)))
        waves
            .spawnMinimus(
                state,
                ColosseumArena.CHEST_MINIMUS,
                ColosseumArena.CHEST_MINIMUS.translateX(-2),
                intro = true,
            )
            ?.infoProtocol
            ?.toggleOps(OpVisibility(1 shl (LEAVE_OP - 1)))
        val chest = waves.resolve(state, ColosseumArena.CHEST) ?: return
        worldQueues.add(chestDelay) {
            if (waves.stateOf(player) !== state) return@add
            sand.clearAll(state)
            locRepo.add(
                chest,
                CHEST_LOC,
                Int.MAX_VALUE,
                LocAngle.North,
                LocShape.CentrepieceStraight,
            )
        }
    }

    fun minimusSay(npc: Npc, player: Player, text: String) {
        npc.say(text)
        player.mes("${MINIMUS_NAME}|$text", ChatType.NpcSay)
    }

    companion object {
        private const val TOXIN_CLEAR_DELAY = 3
        private const val ARENA_TRACK_NAME = "Are You Not Entertained?"
        private const val STOP_MUSIC = "midi.stop_music"
        private const val STOP_MUSIC_FADE = 50
        const val INTERMISSION_QUEUE = "queue.colosseum_intermission"
        const val WAVE_START_QUEUE = "queue.colosseum_wave_start"
        const val CHEST_LOC = "loc.colosseum_reward"
        const val MINIMUS_NAME = "Minimus"

        const val WAVE_START_DELAY = 5
        const val INTERMISSION_DELAY = 3
        const val REINFORCEMENT_DELAY = 66
        private const val LEAVE_OP = 3
    }
}
