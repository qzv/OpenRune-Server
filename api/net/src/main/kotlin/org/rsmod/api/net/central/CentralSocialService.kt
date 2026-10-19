package org.rsmod.api.net.central

import dev.or2.central.worldlink.protocol.social.FriendChatPackets
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.rsmod.api.db.gateway.model.GameDbRequest
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.api.net.central.WorldLinkFrameSpecs.PRIVATE_MESSAGE_MAX_CHARS

class CentralSocialService
@Inject
constructor(
    private val central: OpenRuneCentralWorldLink,
) {
    fun addFriend(
        characterId: Int,
        name: String,
    ): GameDbResult<CentralSocialResult> {
        val cleaned = name.trim()
        if (cleaned.isBlank()) {
            return GameDbResult.Ok(CentralSocialResult.Ignored)
        }

        if (characterId <= 0) {
            return GameDbResult.Ok(
                CentralSocialResult.Failed("Social is not available right now."),
            )
        }

        return GameDbResult.Ok(
            central.addFriend(
                characterId = characterId,
                targetName = cleaned,
            ),
        )
    }

    fun deleteFriend(
        characterId: Int,
        name: String,
    ): GameDbResult<CentralSocialResult> {
        val cleaned = name.trim()
        if (cleaned.isBlank()) {
            return GameDbResult.Ok(CentralSocialResult.Ignored)
        }

        if (characterId <= 0) {
            return GameDbResult.Ok(
                CentralSocialResult.Failed("Social is not available right now."),
            )
        }

        return GameDbResult.Ok(
            central.deleteFriend(
                characterId = characterId,
                targetName = cleaned,
            ),
        )
    }

    fun socialSnapshot(
        characterId: Int,
    ): GameDbResult<OpenRuneCentralWorldLink.CentralSocialSnapshotResult> {
        if (characterId <= 0) {
            return GameDbResult.Ok(
                OpenRuneCentralWorldLink.CentralSocialSnapshotResult.Failed("Social is not available right now."),
            )
        }

        return GameDbResult.Ok(
            central.socialSnapshot(
                characterId = characterId,
            ),
        )
    }

    fun addIgnore(
        characterId: Int,
        name: String,
    ): GameDbResult<CentralSocialResult> {
        val cleaned = name.trim()
        if (cleaned.isBlank()) {
            return GameDbResult.Ok(CentralSocialResult.Ignored)
        }

        if (characterId <= 0) {
            return GameDbResult.Ok(
                CentralSocialResult.Failed("Social is not available right now."),
            )
        }

        return GameDbResult.Ok(
            central.addIgnore(
                characterId = characterId,
                targetName = cleaned,
            ),
        )
    }

    fun deleteIgnore(
        characterId: Int,
        name: String,
    ): GameDbResult<CentralSocialResult> {
        val cleaned = name.trim()
        if (cleaned.isBlank()) {
            return GameDbResult.Ok(CentralSocialResult.Ignored)
        }

        if (characterId <= 0) {
            return GameDbResult.Ok(
                CentralSocialResult.Failed("Social is not available right now."),
            )
        }

        return GameDbResult.Ok(
            central.deleteIgnore(
                characterId = characterId,
                targetName = cleaned,
            ),
        )
    }

    fun sendPrivateMessage(
        fromCharacterId: Int,
        targetName: String,
        senderDisplayName: String,
        senderCrown: Int,
        message: String,
    ): GameDbResult<CentralSocialResult> {
        val cleanedTarget = targetName.trim()
        val cleanedMessage = message.trim().take(PRIVATE_MESSAGE_MAX_CHARS)

        if (cleanedTarget.isBlank() || cleanedMessage.isBlank()) {
            return GameDbResult.Ok(CentralSocialResult.Ignored)
        }

        if (fromCharacterId <= 0) {
            return GameDbResult.Ok(
                CentralSocialResult.Failed("Private messaging is not available right now."),
            )
        }

        return GameDbResult.Ok(
            central.sendPrivateMessage(
                fromCharacterId = fromCharacterId,
                targetName = cleanedTarget,
                senderDisplayName = senderDisplayName,
                senderCrown = senderCrown,
                message = cleanedMessage,
            ),
        )
    }

    fun setChatFilters(
        characterId: Int,
        publicChat: Int,
        privateChat: Int,
        tradeChat: Int,
    ): GameDbResult<CentralSocialResult> {
        if (characterId <= 0) {
            return GameDbResult.Ok(
                CentralSocialResult.Failed("Social settings are not available right now."),
            )
        }

        return GameDbResult.Ok(
            central.setChatFilters(
                characterId = characterId,
                publicChat = publicChat,
                privateChat = privateChat,
                tradeChat = tradeChat,
            ),
        )
    }

    fun setPrivateChatFilter(
        characterId: Int,
        privateChatFilter: Int,
    ): GameDbResult<CentralSocialResult> {
        if (characterId <= 0) {
            return GameDbResult.Ok(
                CentralSocialResult.Failed("Social settings are not available right now."),
            )
        }

        return GameDbResult.Ok(
            central.setPrivateChatFilter(
                characterId = characterId,
                privateChatFilter = privateChatFilter,
            ),
        )
    }
}

data class FriendChatSetup(
    val channelName: String?,
    val enterRank: Int,
    val talkRank: Int,
    val kickRank: Int,
)

/**
 * Chat-channel calls to Central. Each call is queued on one thread as soon as the game thread makes
 * it, so Central sees them in packet order (a kicker's notice line before the kick); the returned
 * request waits for the result on a database worker.
 */
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

    fun join(
        characterId: Int,
        ownerName: String,
    ): GameDbRequest<CentralSocialResult> = ordered { central.joinFriendChat(characterId, ownerName) }

    fun leave(characterId: Int): GameDbRequest<CentralSocialResult> = ordered { central.leaveFriendChat(characterId) }

    fun message(
        characterId: Int,
        senderCrown: Int,
        message: String,
    ): GameDbRequest<CentralSocialResult> =
        ordered { central.sendFriendChatMessage(characterId, senderCrown, message) }

    fun kick(
        characterId: Int,
        targetName: String,
    ): GameDbRequest<CentralSocialResult> = ordered { central.kickFromFriendChat(characterId, targetName) }

    fun setRank(
        characterId: Int,
        targetName: String,
        rank: Int,
    ): GameDbRequest<CentralSocialResult> = ordered { central.setFriendRank(characterId, targetName, rank) }

    fun setChannelName(
        characterId: Int,
        channelName: String,
    ): GameDbRequest<CentralSocialResult> =
        ordered { central.setFriendChatSetting(characterId, FriendChatPackets.SETTING_NAME, 0, channelName) }

    fun setEnterRank(
        characterId: Int,
        rank: Int,
    ): GameDbRequest<CentralSocialResult> =
        ordered { central.setFriendChatSetting(characterId, FriendChatPackets.SETTING_ENTER, rank, "") }

    fun setTalkRank(
        characterId: Int,
        rank: Int,
    ): GameDbRequest<CentralSocialResult> =
        ordered { central.setFriendChatSetting(characterId, FriendChatPackets.SETTING_TALK, rank, "") }

    fun setKickRank(
        characterId: Int,
        rank: Int,
    ): GameDbRequest<CentralSocialResult> =
        ordered { central.setFriendChatSetting(characterId, FriendChatPackets.SETTING_KICK, rank, "") }

    fun settings(characterId: Int): GameDbResult<FriendChatSetup?> {
        val settings = central.friendChatSync(characterId)?.settings ?: return GameDbResult.Ok(null)
        return GameDbResult.Ok(
            FriendChatSetup(settings.channelName, settings.enterRank, settings.talkRank, settings.kickRank),
        )
    }

    fun isInChannel(characterId: Int): Boolean = central.isInFriendChat(characterId)

    fun forget(characterId: Int) {
        central.forgetFriendChat(characterId)
    }

    private fun ordered(call: () -> CentralSocialResult): GameDbRequest<CentralSocialResult> {
        val result = calls.submit(Callable(call))
        return GameDbRequest { GameDbResult.Ok(result.get()) }
    }
}

sealed class CentralSocialResult {
    data object Ok : CentralSocialResult()

    data object Ignored : CentralSocialResult()

    data class Failed(
        val message: String,
    ) : CentralSocialResult()
}
