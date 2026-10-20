package org.rsmod.content.bosses.nex

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onOpLoc2
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

class NexInstance
@Inject
internal constructor(
    registry: BossInstanceRegistry,
    private val fights: NexFights,
    private val spawn: NexSpawn,
) : InstanceScript(registry) {

    private var Player.barrierWarningHidden by boolVarBit("varbit.nex_barrier_warning_hidden")

    override fun settingsRow(): String = SETTINGS_ROW

    override fun area(): InstanceArea = INSTANCE

    override fun ScriptContext.configure() {
        onOpLoc2(BARRIER) { barrierMenu() }
        onOpLoc2(BARRIER_BUSY) { barrierMenu() }
        onEnterPrelude { result, enter ->
            val fader = FADER.asRSCM(RSCMType.COMPONENT)
            runClientScript(FADER_SCRIPT.asRSCM(RSCMType.CLIENTSCRIPT), 1, 1, 1, fader)
            delay(1)
            enter()
            val session = sessionOf(result) ?: return@onEnterPrelude
            spawn.schedule(session, FIRST_SPAWN_DELAY)
        }
        onInstancePlayerJoin {
            val inProgress = fights.of(instanceId)?.inProgress == true
            player.nexBarrier =
                if (inProgress) NexSpawn.BARRIER_NEX_ALIVE else NexSpawn.BARRIER_IN_INSTANCE
        }
        onInstancePlayerLeave { player.nexBarrier = BARRIER_OUTSIDE }
        onInstanceEnded { fights.end(instanceId) }
    }

    private suspend fun ProtectedAccess.barrierMenu() {
        val pick =
            choice3(
                "Start a private fight (your friends will be able to join you).",
                START_PRIVATE,
                "Join a private fight.",
                JOIN_PRIVATE,
                "Do nothing.",
                DO_NOTHING,
                title = MENU_TITLE,
            )
        when (pick) {
            START_PRIVATE -> startPrivate()
            JOIN_PRIVATE -> joinPrivate()
        }
    }

    private suspend fun ProtectedAccess.startPrivate() {
        if (manager.sessionForPlayer(player) != null) return
        if (!player.barrierWarningHidden) {
            mesbox(WARNING)
            val answer =
                choice3(
                    "Yes.",
                    YES,
                    "Yes, and don't ask again.",
                    YES_HIDE,
                    "No.",
                    NO,
                    title = CONFIRM_TITLE,
                )
            if (answer == NO) return
            if (answer == YES_HIDE) player.barrierWarningHidden = true
        }
        val owned = player.uuid?.let { manager.sessionOwnedBy(key, it) }
        val result =
            if (owned != null) {
                manager.join(player, owned, worldClock.cycle, forceAccess = true)
            } else {
                manager.create(player, key, buildSpec(), InstanceAccess.Friends, worldClock.cycle)
            }
        completeInstanceEntry(result) { true }
    }

    private suspend fun ProtectedAccess.joinPrivate() {
        if (manager.sessionForPlayer(player) != null) return
        val name = stringDialog(JOIN_PROMPT).trim()
        if (name.isEmpty()) return
        val session = manager.sessionOwnedByName(key, name) ?: return
        if (fights.of(session.id)?.inProgress == true) {
            mesbox(IN_PROGRESS)
            return
        }
        completeInstanceEntry(manager.join(player, session, worldClock.cycle)) { true }
    }

    private fun sessionOf(result: InstanceManager.Result) =
        when (result) {
            is InstanceManager.Result.Created -> result.session
            is InstanceManager.Result.Joined -> result.session
            else -> null
        }

    private companion object {
        private const val SETTINGS_ROW = "dbrow.instance_nex"
        private const val BARRIER = "loc.nex_fight_barrier"
        private const val BARRIER_BUSY = "loc.nex_fight_barrier_busy"
        private const val FADER_SCRIPT = "clientscript.[clientscript,godwars_fader_init]"
        private const val FADER = "component.godwars_overlay:fader"

        private const val MENU_TITLE = "Select an option"
        private const val CONFIRM_TITLE = "Are you sure you wish to begin?"
        private const val JOIN_PROMPT =
            "Whose fight would you like to join? You must be on their friends list."
        private const val WARNING =
            "You are about to begin an encounter with Nex. Dying during this encounter will " +
                "not be considered a safe death. After entering, you won't be able to leave " +
                "using the barrier. Instead, you'll need to use the altar to teleport out. " +
                "Are you sure you wish to begin?"
        private const val IN_PROGRESS =
            "The fight with Nex is already in progress. You'll have to wait until the next " +
                "fight begins."

        private const val START_PRIVATE = 1
        private const val JOIN_PRIVATE = 2
        private const val DO_NOTHING = 3
        private const val YES = 1
        private const val YES_HIDE = 2
        private const val NO = 3

        private const val BARRIER_OUTSIDE = 1
        private const val FIRST_SPAWN_DELAY = 54

        private val INSTANCE = InstanceArea.copyRegions(regionIds = listOf(11601), level = 0)
    }
}
