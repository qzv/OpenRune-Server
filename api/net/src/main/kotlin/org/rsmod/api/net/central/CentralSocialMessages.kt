package org.rsmod.api.net.central

import dev.or2.central.worldlink.protocol.WorldOpcodes

internal fun socialFailMessage(reason: Int): String =
    when (reason) {
        WorldOpcodes.SOCIAL_FAIL_USER_NOT_FOUND -> "Unable to find player."
        WorldOpcodes.SOCIAL_FAIL_SELF_ACTION -> "You can't perform that action on yourself."
        WorldOpcodes.SOCIAL_FAIL_ALREADY_FRIEND -> "That player is already on your friend list."
        WorldOpcodes.SOCIAL_FAIL_ALREADY_IGNORED -> "That player is already on your ignore list."
        WorldOpcodes.SOCIAL_FAIL_NOT_ACCEPTING_PRIVATE -> "Player is not accepting private messages."
        WorldOpcodes.SOCIAL_FAIL_NOT_LOGGED_IN -> "Player is offline."
        WorldOpcodes.SOCIAL_FAIL_LIST_FULL -> "Your list is full."
        WorldOpcodes.SOCIAL_FAIL_FRIENDCHAT_NOT_FOUND -> FriendChatMessages.DOES_NOT_EXIST
        WorldOpcodes.SOCIAL_FAIL_FRIENDCHAT_BANNED -> FriendChatMessages.BANNED
        WorldOpcodes.SOCIAL_FAIL_FRIENDCHAT_RANK_TOO_LOW -> FriendChatMessages.RANK_TOO_LOW
        WorldOpcodes.SOCIAL_FAIL_FRIENDCHAT_NOT_ALLOWED -> FriendChatMessages.NOT_ALLOWED
        WorldOpcodes.SOCIAL_FAIL_FRIENDCHAT_ALREADY_IN_CHANNEL -> FriendChatMessages.PLEASE_WAIT
        else -> "Unable to complete that action."
    }
