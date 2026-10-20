package org.rsmod.api.net.central

import dev.or2.central.worldlink.protocol.social.FriendChatPackets
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.rsmod.api.db.gateway.model.GameDbRequest
import org.rsmod.api.db.gateway.model.GameDbResult

data class FriendChatSetup(
    val channelName: String?,
    val enterRank: Int,
    val talkRank: Int,
    val kickRank: Int,
)

/** Queues every call on one thread in packet order, so Central sees a notice before its kick. */
@Singleton
class CentralFriendChatService
@Inject
constructor(
    private val central: OpenRuneCentralWorldLink,
) {
    private val calls =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "openrune-central-friendchat").apply { isDaemon = true }
        }

    fun join(characterId: Int, ownerName: String): GameDbRequest<CentralSocialResult> =
        ordered { central.joinFriendChat(characterId, ownerName) }

    fun leave(characterId: Int): GameDbRequest<CentralSocialResult> =
        ordered { central.leaveFriendChat(characterId) }

    fun message(
        characterId: Int,
        senderCrown: Int,
        message: String,
    ): GameDbRequest<CentralSocialResult> =
        ordered { central.sendFriendChatMessage(characterId, senderCrown, message) }

    fun kick(characterId: Int, targetName: String): GameDbRequest<CentralSocialResult> =
        ordered { central.kickFromFriendChat(characterId, targetName) }

    fun setRank(
        characterId: Int,
        targetName: String,
        rank: Int,
    ): GameDbRequest<CentralSocialResult> =
        ordered { central.setFriendRank(characterId, targetName, rank) }

    fun setChannelName(characterId: Int, channelName: String): GameDbRequest<CentralSocialResult> =
        setting(characterId, FriendChatPackets.SETTING_NAME, 0, channelName)

    fun setEnterRank(characterId: Int, rank: Int): GameDbRequest<CentralSocialResult> =
        setting(characterId, FriendChatPackets.SETTING_ENTER, rank)

    fun setTalkRank(characterId: Int, rank: Int): GameDbRequest<CentralSocialResult> =
        setting(characterId, FriendChatPackets.SETTING_TALK, rank)

    fun setKickRank(characterId: Int, rank: Int): GameDbRequest<CentralSocialResult> =
        setting(characterId, FriendChatPackets.SETTING_KICK, rank)

    fun settings(characterId: Int): GameDbResult<FriendChatSetup?> {
        val settings = central.friendChatSync(characterId)?.settings ?: return GameDbResult.Ok(null)
        val setup =
            FriendChatSetup(
                settings.channelName,
                settings.enterRank,
                settings.talkRank,
                settings.kickRank,
            )
        return GameDbResult.Ok(setup)
    }

    fun isInChannel(characterId: Int): Boolean = central.isInFriendChat(characterId)

    fun forget(characterId: Int) {
        central.forgetFriendChat(characterId)
    }

    private fun setting(
        characterId: Int,
        setting: Int,
        rank: Int,
        channelName: String = "",
    ): GameDbRequest<CentralSocialResult> =
        ordered { central.setFriendChatSetting(characterId, setting, rank, channelName) }

    private fun ordered(call: () -> CentralSocialResult): GameDbRequest<CentralSocialResult> {
        val result = calls.submit(Callable(call))
        return GameDbRequest { GameDbResult.Ok(result.get()) }
    }
}
