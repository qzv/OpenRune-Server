package org.rsmod.content.minigames.colosseum

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statRestoreAll
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerHit
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.api.script.onPlayerSoftQueue
import org.rsmod.content.minigames.colosseum.modifiers.FlareOrbit
import org.rsmod.content.minigames.colosseum.modifiers.SolarflareModifier
import org.rsmod.content.minigames.colosseum.modifiers.VolatilityModifier
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

class ColosseumInstance
@Inject
internal constructor(
    registry: BossInstanceRegistry,
    private val run: ColosseumRun,
    private val waves: ColosseumWaves,
    private val rewards: ColosseumRewards,
    private val intermission: ColosseumIntermission,
    private val npcDeath: NpcDeath,
    private val worldQueues: WorldQueueList,
    private val modifiers: ColosseumModifiers,
    private val solarflare: SolarflareModifier,
    private val volatility: VolatilityModifier,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
) : InstanceScript(registry) {

    override fun settingsRow(): String = SETTINGS_ROW

    override fun area(): InstanceArea = INSTANCE

    override fun runsPreludeOnFreshRun(): Boolean = true

    override fun destroyWhenEmpty(): Boolean = true

    override fun logoutExits(): List<CoordGrid> = ColosseumArena.logoutExits()

    override fun graveCoords(): CoordGrid = ColosseumArena.GRAVE

    override fun keepUntradeablesOnDeath(): Boolean = false

    override fun ScriptContext.configure() {
        onEnterPrelude { result, enter ->
            val session =
                when (result) {
                    is InstanceManager.Result.Created -> result.session
                    is InstanceManager.Result.Joined -> result.session
                    else -> return@onEnterPrelude
                }
            fadeOverlay(0, FADE_CLEAR, 0, FADE_OPAQUE, FADE_CYCLES)
            delay(1)
            minimapHideMap()
            delay(FADE_OUT_TICKS - 1)
            enter()
            waves.open(player, session)
            run.reset(player)
            delay(1)
            fadeOverlay(0, FADE_OPAQUE, 0, FADE_CLEAR, FADE_CYCLES)
            minimapReset()
            manager.resolveCoord(session, ColosseumArena.WALK_IN_END)?.let { walk(it) }
            player.queue(INTRO_QUEUE, MINIMUS_ARRIVAL_DELAY)
        }
        onEnterObject { if (confirmEntry()) defaultInstanceEntry() }
        onExitObject { leave() }
        onInstancePlayerLeave {
            run.clear(player)
            player.statRestoreAll(allStats)
            manager.sessionForPlayer(player)?.let { waves.close(it) }
        }
        onInstanceEnded { waves.close(instanceId) }

        onPlayerQueue(INTRO_QUEUE) { introduceMinimus() }
        onPlayerQueue(ColosseumRun.INTERMISSION_QUEUE) {
            modifiers.rollOffers(player, player.runWave + 1)
            openIntermission(announceChoice = false, spawnMinimus = true)
        }
        onPlayerQueue(ColosseumRun.WAVE_START_QUEUE) {
            if (run.startWave(player)) run.solStarter?.invoke(this)
        }
        onPlayerSoftQueue(FlareOrbit.PRAYER_QUEUE) { solarflare.disableProtection(player) }

        onOpNpc1(ColosseumNpcs.MINIMUS) { startWave() }
        onOpNpc3(ColosseumNpcs.MINIMUS) {
            if (player.runState == RunState.Intermission) leaveEarly(it.npc) else leave()
        }

        onIfModalButton("$INTERMISSION:mod_button_1") { with(intermission) { select(1) } }
        onIfModalButton("$INTERMISSION:mod_button_2") { with(intermission) { select(2) } }
        onIfModalButton("$INTERMISSION:mod_button_3") { with(intermission) { select(3) } }
        onIfModalButton("$INTERMISSION:right_button") { confirm() }
        onIfModalButton("$INTERMISSION:confirm_confirm") { claimEarly() }
        onIfModalButton("$INTERMISSION:left_loot") {
            with(intermission) { examine(rewards.previous(player), it.comsub) }
        }
        onIfModalButton("$INTERMISSION:right_loot") {
            with(intermission) { examine(rewards.future(player), it.comsub) }
        }

        onOpLoc1(ColosseumRun.CHEST_LOC) {
            if (player.runState != RunState.Finished) return@onOpLoc1
            with(rewards) { openChest() }
        }
        onIfModalButton("${ColosseumRewards.CHEST}:tab_content_2_content") {
            with(rewards) {
                when (it.op) {
                    IfButtonOp.Op1 -> take(it.comsub, 1)
                    IfButtonOp.Op2 -> take(it.comsub, TAKE_FIVE)
                    IfButtonOp.Op3 -> take(it.comsub, Int.MAX_VALUE)
                    IfButtonOp.Op4 -> take(it.comsub, countDialog())
                    IfButtonOp.Op5 -> examine(it.comsub)
                    else -> Unit
                }
            }
        }
        onIfModalButton("${ColosseumRewards.CHEST}:tab_content_2_button_1") {
            with(rewards) { discard() }
        }
        onIfModalButton("${ColosseumRewards.CHEST}:tab_content_2_button_2") {
            with(rewards) { takeAll(inv) }
        }
        onIfModalButton("${ColosseumRewards.CHEST}:tab_content_2_button_3") {
            with(rewards) { takeAll(bank) }
        }

        onPlayerHit {
            if (player.runState != RunState.WaveActive || hit.type == HitType.Typeless)
                return@onPlayerHit
            player.waveDamageTaken += hit.damage
        }

        onNpcQueue(ColosseumNpcs.ENEMY_CONTENT, DEATH_QUEUE) {
            val state = waves.stateOf(npc)
            state?.let { volatility.onEnemyDeath(it, npc) }
            npcDeath.deathNoDrops(this)
            if (state != null) {
                waves.enemyDespawned(state, npc)
                run.onEnemyRemoved(state, npc)
            }
        }
    }

    private fun ProtectedAccess.introduceMinimus() {
        val state = waves.stateOf(player) ?: return
        val minimus =
            waves.spawnMinimus(
                state,
                ColosseumArena.MINIMUS_START,
                ColosseumArena.MINIMUS_START.translateZ(-1),
                true,
            ) ?: return
        run.minimusSay(minimus, player, "A ${ColosseumRank.title(player.bestGlory)} approaches!")
        val target = player
        worldQueues.add(SECOND_GREETING_DELAY) {
            if (state.minimus !== minimus) return@add
            run.minimusSay(minimus, target, READY_PROMPT)
            if (target.runState == RunState.Entering) target.runState = RunState.AwaitingStart
        }
    }

    private fun ProtectedAccess.startWave() {
        when (player.runState) {
            RunState.AwaitingStart -> openIntermission(announceChoice = false, spawnMinimus = false)
            RunState.Intermission -> openIntermission(announceChoice = true, spawnMinimus = false)
            else -> Unit
        }
    }

    private fun ProtectedAccess.openIntermission(announceChoice: Boolean, spawnMinimus: Boolean) {
        if (player.runState != RunState.Intermission && player.runState != RunState.AwaitingStart)
            return
        if (spawnMinimus) {
            val state = waves.stateOf(player) ?: return
            val tile =
                MINIMUS_SIDES.map { (dx, dz) -> player.coords.translate(dx, dz) }
                    .firstOrNull { !isWalkBlocked(it) }
            val template = tile?.let { waves.templateOf(state, it) } ?: return
            val face = waves.templateOf(state, player.coords) ?: return
            waves.spawnMinimus(state, template, face, intro = true)
        }
        if (announceChoice) mes(CHOICE_REQUIRED_MESSAGE)
        stopAction()
        with(intermission) { open() }
    }

    private fun ProtectedAccess.confirm() {
        if (player.selectedModifier == 0) {
            mes(SELECT_FIRST_MESSAGE)
            return
        }
        if (!run.confirmModifier(player)) return
        with(intermission) { close() }
        soundSynth(CONFIRM_SYNTH)
    }

    private fun ProtectedAccess.claimEarly() {
        if (player.runState != RunState.Intermission) return
        with(intermission) { close() }
        run.finish(player, CHEST_SPAWN_DELAY)
    }

    private suspend fun ProtectedAccess.leaveEarly(minimus: Npc) {
        mes(CHOICE_REQUIRED_MESSAGE)
        startDialogue(minimus) {
            chatNpc(neutral, LEAVE_WARNING)
            val confirmed = choice2("Yes.", true, "No.", false, title = LEAVE_EARLY_PROMPT)
            if (!confirmed) return@startDialogue
            with(access) { claimEarly() }
            chatNpc(neutral, leaveEarlyFarewell(player.bestGlory))
        }
    }

    private suspend fun ProtectedAccess.leave() {
        val unclaimed = rewards.earned(player).objs.any { it != null }
        val confirmed =
            if (unclaimed) choice2("No.", false, "Yes.", true, title = EXIT_PROMPT)
            else choice2("Yes.", true, "No.", false, title = LEAVE_PROMPT)
        if (!confirmed) return
        val session = manager.sessionForPlayer(player) ?: return
        fadeOverlay(0, FADE_CLEAR, 0, FADE_OPAQUE, FADE_CYCLES)
        delay(1)
        minimapHideMap()
        delay(1)
        player.runState = RunState.Leaving
        manager.leave(player, session, worldClock.cycle)
        telejump(ColosseumArena.randomLobbyExit(random), TeleportType.Exempt)
        delay(2)
        run.commitGlory(player)
        rewards.stopTransmit(player)
        player.lootOpened = false
        fadeOverlay(0, FADE_OPAQUE, 0, FADE_CLEAR, FADE_CYCLES)
        minimapReset()
    }

    private fun isWalkBlocked(tile: CoordGrid): Boolean = collision.isWalkBlocked(tile)

    private val allStats by lazy {
        ServerCacheManager.getStats().values.map { RSCM.getReverseMapping(RSCMType.STAT, it.id) }
    }

    private suspend fun ProtectedAccess.confirmEntry(): Boolean {
        if (player.entryWarningDisabled) return true
        mesbox(ENTRY_WARNING)
        val choice =
            choice3(
                "Yes, I understand the risks.",
                ENTRY_ONCE,
                "Yes, and don't ask again.",
                ENTRY_ALWAYS,
                "No.",
                ENTRY_CANCEL,
                title = ENTRY_TITLE,
            )
        if (choice == ENTRY_ALWAYS) player.entryWarningDisabled = true
        return choice != ENTRY_CANCEL
    }

    internal companion object {
        const val SETTINGS_ROW = "dbrow.instance_fortis_colosseum"

        private const val INTERMISSION = ColosseumIntermission.COMPONENT
        private const val ENTRY_TITLE = "Are you sure you wish to enter the Fortis Colosseum?"
        private const val ENTRY_WARNING =
            "You are about to enter the Fortis Colosseum. Within, you will face multiple " +
                "waves of <col=b30000>deadly foes</col>. Dying at any point is " +
                "<col=b30000>not considered a safe death</col>. Apart from between waves, there " +
                "is no exit within the Colosseum, but teleporting out is allowed."
        private const val ENTRY_ONCE = 1
        private const val ENTRY_ALWAYS = 2
        private const val ENTRY_CANCEL = 3
        private const val INTRO_QUEUE = "queue.colosseum_arena_intro"
        private const val DEATH_QUEUE = "queue.death"

        private const val READY_PROMPT = "Let me know when you want to begin."
        private const val LEAVE_PROMPT = "Are you sure you wish to leave?"
        private const val EXIT_PROMPT =
            "Are you sure you wish to exit?<br>You will lose all rewards inside the reward chest."
        private const val LEAVE_EARLY_PROMPT = "Are you sure you wish to leave early?"
        private const val CHOICE_REQUIRED_MESSAGE = "You must make a choice before continuing!"
        private const val SELECT_FIRST_MESSAGE =
            "<col=ff3045>You must select a modifier first.</col>"
        private const val LEAVE_WARNING =
            "Leaving now will forfeit your run, with you only being rewarded the items you " +
                "have earned so far."

        private fun leaveEarlyFarewell(glory: Int) =
            "It was a valiant effort, ${ColosseumRank.title(glory)}. I've popped all your " +
                "earned rewards into that chest over there. You can retrieve them from it when " +
                "ready."

        private const val FADE_CLEAR = 255
        private const val FADE_OPAQUE = 0
        private const val FADE_CYCLES = 50
        private const val FADE_OUT_TICKS = 3
        private const val MINIMUS_ARRIVAL_DELAY = 4
        private const val CONFIRM_SYNTH = "synth.colosseum_modifier_confirm"
        private val MINIMUS_SIDES = listOf(0 to 1, 1 to 0, -1 to 0)
        private const val SECOND_GREETING_DELAY = 3
        private const val CHEST_SPAWN_DELAY = 1
        private const val TAKE_FIVE = 5

        private val INSTANCE = InstanceArea.copyRegions(centerRegionId = ColosseumArena.REGION)
    }
}
