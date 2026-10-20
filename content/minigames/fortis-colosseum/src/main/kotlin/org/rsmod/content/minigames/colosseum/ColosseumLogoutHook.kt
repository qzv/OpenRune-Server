package org.rsmod.content.minigames.colosseum

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.PlayerLogoutRequestHook
import org.rsmod.api.player.protect.ProtectedAccess

@Singleton
internal class ColosseumLogoutHook @Inject constructor(private val waves: ColosseumWaves) :
    PlayerLogoutRequestHook {
    override suspend fun intercept(access: ProtectedAccess): Boolean {
        val player = access.player
        if (player.runState == RunState.None || waves.stateOf(player) == null) return false
        if (access.choice2("Yes.", true, "No.", false, title = LOGOUT_PROMPT)) access.logOut()
        return true
    }

    companion object {
        const val LOGOUT_PROMPT =
            "Are you sure you wish to log out? <br>You will lose any rewards you may have " +
                "earned in the Colosseum."
        const val WAVE_START_MESSAGE = "You cannot logout at the start of a wave!"
    }
}
