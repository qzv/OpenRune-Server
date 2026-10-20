package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.messaging.MessagePublic
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.net.central.CentralFriendChatService
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PublicMessage

class MessagePublicHandler
@Inject
constructor(
    private val db: GameDbManager,
    private val friendChat: CentralFriendChatService,
) : MessageHandler<MessagePublic> {
    override fun handle(player: Player, message: MessagePublic) {
        if (message.type == FRIEND_CHAT_TYPE) {
            player.sendFriendChatMessage(message.message)
            return
        }
        /*
         * TODO(social): Ignore list currently blocks ignored public chat from the
         *  chatbox through the client ignore list, but overhead public-chat bubbles are
         *  still sent by the player-info public-message mask. For stricter ignore
         *  behavior, possibly filter PublicMessage masks per receiver before sending player info.
         *
         * TODO(trade):
         *  - When trade is implemented, trade requests should be blocked when
         *  either side has the other ignored.
         */

        val publicMessage =
            PublicMessage(
                text = message.message,
                colour = message.colour,
                effect = message.effect,
                clanType = if (message.clanType == -1) null else message.clanType,
                modIcon = player.modLevel.clientCode,
                autoTyper = false,
                pattern = message.pattern?.asByteArray(),
            )
        player.publicMessage = publicMessage
    }

    private fun Player.sendFriendChatMessage(raw: String) {
        val characterId = characterId
        val text =
            raw.removePrefix(FRIEND_CHAT_PREFIX).trim().replaceFirstChar { it.uppercaseChar() }
        if (characterId <= 0 || text.isEmpty() || !friendChat.isInChannel(characterId)) {
            return
        }
        val crown = modLevel.clientCode
        db.request(request = friendChat.message(characterId, crown, text), response = {})
    }

    private companion object {
        const val FRIEND_CHAT_TYPE = 2
        const val FRIEND_CHAT_PREFIX = "/"
    }
}
