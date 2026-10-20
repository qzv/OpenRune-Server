package org.rsmod.content.bosses.yama

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenFullOverlay
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

@Singleton
internal class YamaContractOverlay @Inject constructor(private val eventBus: EventBus) {
    fun open(player: Player, contract: YamaContract?) {
        val model = contract?.overlayModel ?: return
        player.ifOpenFullOverlay(OVERLAY, eventBus)
        val script = SETUP_SCRIPT.asRSCM(RSCMType.CLIENTSCRIPT)
        player.runClientScript(script, contract.item.asRSCM(RSCMType.OBJ), model)
    }

    fun close(player: Player) = player.ifCloseOverlay(OVERLAY, eventBus)

    private companion object {
        private const val OVERLAY = "interface.yama_contract_fight"
        private const val SETUP_SCRIPT = "clientscript.[clientscript,yama_contract_setup]"
    }
}
