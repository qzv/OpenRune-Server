package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.friendchat.FriendChatSetRank
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.net.central.CentralFriendChatService
import org.rsmod.game.entity.Player

class FriendChatSetRankHandler
@Inject
constructor(
    private val db: GameDbManager,
    private val friendChat: CentralFriendChatService,
) : MessageHandler<FriendChatSetRank> {
    override fun handle(player: Player, message: FriendChatSetRank) {
        val characterId = player.characterId
        if (characterId <= 0 || message.rank !in NOT_RANKED..GENERAL) {
            return
        }
        val targetName = message.name
        val rank = message.rank
        db.request(request = friendChat.setRank(characterId, targetName, rank), response = {})
    }

    private companion object {
        const val NOT_RANKED = 0
        const val GENERAL = 6
    }
}
