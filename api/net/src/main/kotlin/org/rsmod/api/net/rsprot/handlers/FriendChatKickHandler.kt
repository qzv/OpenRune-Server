package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.friendchat.FriendChatKick
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.fold
import org.rsmod.api.net.central.CentralFriendChatService
import org.rsmod.api.net.central.CentralSocialResult
import org.rsmod.api.net.central.FriendChatMessages
import org.rsmod.api.net.central.friendChatMes
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

class FriendChatKickHandler
@Inject
constructor(
    private val playerList: PlayerList,
    private val db: GameDbManager,
    private val friendChat: CentralFriendChatService,
) : MessageHandler<FriendChatKick> {
    override fun handle(player: Player, message: FriendChatKick) {
        val characterId = player.characterId
        if (characterId <= 0 || !friendChat.isInChannel(characterId)) {
            return
        }
        val uid = player.uid
        val targetName = message.name
        db.request(
            request = { friendChat.kick(characterId, targetName) },
            response = { result ->
                val current = uid.resolve(playerList) ?: return@request
                result.fold(
                    onOk = { kickResult ->
                        if (kickResult == CentralSocialResult.Ok) {
                            current.friendChatMes(FriendChatMessages.KICK_SUCCESSFUL)
                        }
                    },
                    onErr = {},
                )
            },
        )
    }
}
