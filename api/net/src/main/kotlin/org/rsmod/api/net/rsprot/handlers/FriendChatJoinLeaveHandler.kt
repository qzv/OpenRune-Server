package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.friendchat.FriendChatJoinLeave
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.fold
import org.rsmod.api.net.central.CentralFriendChatService
import org.rsmod.api.net.central.CentralSocialResult
import org.rsmod.api.net.central.FriendChatMessages
import org.rsmod.api.net.central.friendChatMes
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

class FriendChatJoinLeaveHandler
@Inject
constructor(
    private val playerList: PlayerList,
    private val db: GameDbManager,
    private val friendChat: CentralFriendChatService,
) : MessageHandler<FriendChatJoinLeave> {
    override fun handle(player: Player, message: FriendChatJoinLeave) {
        val characterId = player.characterId
        if (characterId <= 0) {
            return
        }
        val ownerName = message.name
        if (ownerName == null) {
            if (friendChat.isInChannel(characterId)) {
                db.request(request = { friendChat.leave(characterId) }, response = {})
            }
            return
        }
        if (friendChat.isInChannel(characterId)) {
            player.friendChatMes(FriendChatMessages.PLEASE_WAIT)
            return
        }
        if (!ownerName.isValidOwnerName()) {
            player.friendChatMes(FriendChatMessages.DOES_NOT_EXIST)
            return
        }
        player.friendChatMes(FriendChatMessages.ATTEMPTING_TO_JOIN)
        val uid = player.uid
        db.request(
            request = { friendChat.join(characterId, ownerName) },
            response = { result ->
                val current = uid.resolve(playerList) ?: return@request
                result.fold(
                    onOk = { joinResult ->
                        if (joinResult is CentralSocialResult.Failed) {
                            current.friendChatMes(joinResult.message)
                        }
                    },
                    onErr = {},
                )
            },
        )
    }

    private fun String.isValidOwnerName(): Boolean =
        length in 1..MAX_NAME_LENGTH && all { it.isLetterOrDigit() || it in NAME_SEPARATORS }

    private companion object {
        const val MAX_NAME_LENGTH = 12
        const val NAME_SEPARATORS = " _- "
    }
}
