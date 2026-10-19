package org.rsmod.api.net.central

import dev.or2.central.worldlink.protocol.social.FriendChatPackets
import net.rsprot.protocol.game.outgoing.friendchat.MessageFriendChannel
import net.rsprot.protocol.game.outgoing.friendchat.UpdateFriendChatChannelFull
import net.rsprot.protocol.game.outgoing.friendchat.UpdateFriendChatChannelFullV2
import net.rsprot.protocol.game.outgoing.friendchat.UpdateFriendChatChannelSingleUser
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.entity.Player

public object FriendChatMessages {
    public const val ATTEMPTING_TO_JOIN: String = "Attempting to join chat-channel..."
    public const val NOW_TALKING: String = "Now talking in chat-channel "
    public const val TO_TALK: String = "To talk, start each line of chat with the / symbol."
    public const val LEFT: String = "You have left the channel."
    public const val DOES_NOT_EXIST: String = "The channel you tried to join does not exist."
    public const val PLEASE_WAIT: String = "Please wait until you are logged out of your previous chat-channel."
    public const val BANNED: String = "You are temporarily banned from this chat-channel."
    public const val RANK_TOO_LOW: String = "You do not have a high enough rank to join this chat-channel."
    public const val NOT_ALLOWED: String = "You are not allowed to join this user's chat-channel."
    public const val KICK_SUCCESSFUL: String = "Your request to kick/ban this user was successful."
    public const val KICKED: String = "You have been kicked from the channel."
    public const val REMOVED: String = "You have been removed from this channel."
    public const val DISABLED: String = "Your chat-channel has now been disabled!"
    public const val ENABLED: String = "Your chat-channel has now been enabled!"
    public const val JOIN_YOUR_CHANNEL: String = "Join your channel by clicking 'Join Chat' and typing: "
    public const val INVALID_NAME: String = "Invalid chat-channel name entered!"
    public const val CHANGES_PENDING: String =
        "Changes will take effect on your friends channel in the next 60 seconds."
}

public fun Player.friendChatMes(text: String) {
    mes(text, ChatType.FriendsChatNotification)
}

public fun Player.writeFriendChatNotInChannel() {
    client.write(UpdateFriendChatChannelFullV2(UpdateFriendChatChannelFullV2.LeaveUpdate))
}

internal fun Player.writeCentralFriendChatChannel(
    push: ServerFriendChatChannelPayload,
    serverConfig: ServerConfig,
) {
    if (push.joined) {
        friendChatMes(FriendChatMessages.NOW_TALKING + push.channelName)
        friendChatMes(FriendChatMessages.TO_TALK)
    }
    val entries =
        push.members.map { member ->
            UpdateFriendChatChannelFull.FriendChatEntry(
                name = member.displayName,
                worldId = member.worldId,
                rank = member.rank,
                worldName = formatWorldName(member.worldId, serverConfig),
            )
        }
    client.write(
        UpdateFriendChatChannelFullV2(
            UpdateFriendChatChannelFullV2.JoinUpdate(
                channelOwner = push.ownerName,
                channelName = push.channelName,
                kickRank = push.kickRank,
                entries = entries,
            ),
        ),
    )
}

internal fun Player.writeCentralFriendChatMember(
    push: ServerFriendChatMemberPayload,
    serverConfig: ServerConfig,
) {
    val member = push.member
    val user =
        if (push.added) {
            UpdateFriendChatChannelSingleUser.AddedFriendChatUser(
                member.displayName,
                member.worldId,
                member.rank,
                formatWorldName(member.worldId, serverConfig),
            )
        } else {
            UpdateFriendChatChannelSingleUser.RemovedFriendChatUser(member.displayName, member.worldId)
        }
    client.write(UpdateFriendChatChannelSingleUser(user))
}

internal fun Player.writeCentralFriendChatLeft(push: ServerFriendChatLeftPayload) {
    val message =
        when (push.reason) {
            FriendChatPackets.LEFT_REASON_KICKED -> FriendChatMessages.KICKED
            FriendChatPackets.LEFT_REASON_REMOVED -> FriendChatMessages.REMOVED
            else -> FriendChatMessages.LEFT
        }
    friendChatMes(message)
    writeFriendChatNotInChannel()
}

internal fun Player.writeCentralFriendChatMessage(push: ServerFriendChatMessagePayload) {
    client.write(
        MessageFriendChannel(
            sender = push.senderName,
            channelName = push.channelName,
            worldId = push.senderWorldId,
            worldMessageCounter = nextPrivateMessageCounter(push.senderWorldId),
            chatCrownType = push.senderCrown,
            message = push.message,
        ),
    )
}

internal fun Player.writeCentralFriendChatNotice(push: ServerFriendChatNoticePayload) {
    when (push.notice) {
        FriendChatPackets.NOTICE_CHANGES_PENDING -> mes(FriendChatMessages.CHANGES_PENDING, ChatType.FriendNotification)
        FriendChatPackets.NOTICE_ENABLED -> {
            friendChatMes(FriendChatMessages.ENABLED)
            friendChatMes(FriendChatMessages.JOIN_YOUR_CHANNEL + displayName)
        }
        FriendChatPackets.NOTICE_DISABLED -> friendChatMes(FriendChatMessages.DISABLED)
    }
}
