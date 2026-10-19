package org.rsmod.content.interfaces.chatchannel

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.GameDbRequest
import org.rsmod.api.db.gateway.model.fold
import org.rsmod.api.net.central.CentralFriendChatService
import org.rsmod.api.net.central.CentralSocialResult
import org.rsmod.api.net.central.FriendChatMessages
import org.rsmod.api.net.central.friendChatMes
import org.rsmod.api.net.central.writeFriendChatNotInChannel
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ChatChannelScript
@Inject
constructor(
    private val eventBus: EventBus,
    private val db: GameDbManager,
    private val playerList: PlayerList,
    private val friendChat: CentralFriendChatService,
) : PluginScript() {
    private var Player.sideChannelsTab by intVarBit("varbit.side_channels_tab_selected")
    private var Player.inChannelOnLogout by intVarBit("varbit.chatchannel_on_logout")

    override fun ScriptContext.startup() {
        onIfOverlayButton("component.side_channels:tab_0") { player.selectChatChannelTab() }
        onIfOverlayButton("component.chatchannel_current:setup") { player.openSetup() }
        onIfModalButton(OPT_PREFIX) { setPrefix(it) }
        onIfModalButton(OPT_ENTER) { setThreshold(it, OPT_ENTER, friendChat::setEnterRank) }
        onIfModalButton(OPT_TALK) { setThreshold(it, OPT_TALK, friendChat::setTalkRank) }
        onIfModalButton(OPT_KICK) { setThreshold(it, OPT_KICK, friendChat::setKickRank) }
        onEvent<SessionStateEvent.Login> { player.inChannelOnLogout = 0 }
        onEvent<SessionStateEvent.Logout> { player.leaveOnLogout() }
    }

    private fun Player.selectChatChannelTab() {
        sideChannelsTab = 0
        ifOpenOverlay("interface.chatchannel_current", "component.side_channels:contents", eventBus)
    }

    private fun Player.openSetup() {
        val characterId = characterId
        if (characterId <= 0) {
            return
        }
        val uid = uid
        db.request(
            request = { friendChat.settings(characterId) },
            response = { result ->
                val current = uid.resolve(playerList) ?: return@request
                result.fold(
                    onOk = { settings ->
                        settings ?: return@fold
                        current.ifSetText(OPT_ENTER, thresholdLabel(OPT_ENTER, settings.enterRank))
                        current.ifSetText(OPT_TALK, thresholdLabel(OPT_TALK, settings.talkRank))
                        current.ifSetText(OPT_KICK, thresholdLabel(OPT_KICK, settings.kickRank))
                        current.ifSetText(OPT_PREFIX, settings.channelName ?: CHAT_DISABLED)
                        current.ifOpenMainModal("interface.chatchannel_setup", eventBus)
                    },
                    onErr = {},
                )
            },
        )
    }

    private suspend fun ProtectedAccess.setPrefix(event: IfModalButton) {
        val characterId = player.characterId
        if (event.op.slot == DISABLE_OP) {
            ifSetText(OPT_PREFIX, CHAT_DISABLED)
            update(friendChat.setChannelName(characterId, ""))
            return
        }
        val input = stringDialog(PREFIX_PROMPT, mode = PREFIX_PROMPT_MODE)
        val name = ChatChannelName.format(input)
        if (name == null) {
            player.friendChatMes(FriendChatMessages.INVALID_NAME)
            return
        }
        ifSetText(OPT_PREFIX, name)
        update(friendChat.setChannelName(characterId, name))
    }

    private fun ProtectedAccess.setThreshold(
        event: IfModalButton,
        component: String,
        apply: (Int, Int) -> GameDbRequest<CentralSocialResult>,
    ) {
        val rank = event.op.slot - OP_TO_RANK
        val label = thresholdLabel(component, rank)
        if (label.isEmpty()) {
            return
        }
        ifSetText(component, label)
        val characterId = player.characterId
        update(apply(characterId, rank))
    }

    private fun update(request: GameDbRequest<CentralSocialResult>) {
        db.request(request = request, response = {})
    }

    private fun Player.leaveOnLogout() {
        val characterId = characterId
        if (friendChat.isInChannel(characterId)) {
            inChannelOnLogout = 1
            friendChatMes(FriendChatMessages.LEFT)
            writeFriendChatNotInChannel()
        }
        friendChat.forget(characterId)
    }

    private fun thresholdLabel(component: String, rank: Int): String {
        val ops = ServerCacheManager.fromComponent(component.asRSCM(RSCMType.COMPONENT)).op
        return ops?.getOrNull(rank + OP_TO_RANK - 1).orEmpty()
    }

    private companion object {
        const val OPT_PREFIX = "component.chatchannel_setup:opt_prefix"
        const val OPT_ENTER = "component.chatchannel_setup:opt_enter"
        const val OPT_TALK = "component.chatchannel_setup:opt_talk"
        const val OPT_KICK = "component.chatchannel_setup:opt_kick"
        const val CHAT_DISABLED = "Chat disabled"
        const val PREFIX_PROMPT = "Enter chat prefix:"
        const val PREFIX_PROMPT_MODE = -1
        const val DISABLE_OP = 2
        const val OP_TO_RANK = 2
    }
}
