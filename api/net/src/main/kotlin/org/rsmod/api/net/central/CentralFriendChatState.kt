package org.rsmod.api.net.central

import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Singleton
public class CentralFriendChatState {
    internal val channels = ConcurrentHashMap<Int, String>()

    internal val friendRanks = ConcurrentHashMap<Int, ConcurrentHashMap<String, Int>>()
}
