package org.rsmod.content.minigames.colosseum

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.inv.Inventory

@Singleton
internal class ColosseumIntermission @Inject constructor(private val rewards: ColosseumRewards) {
    fun ProtectedAccess.open() {
        val earned = rewards.earned(player)
        val future = rewards.future(player)
        val previous = rewards.previous(player)
        invTransmit(earned)
        invTransmit(future)
        invTransmit(previous)
        ifOpenMainModal(INTERFACE)
        runClientScript(
            INIT_SCRIPT.asRSCM(RSCMType.CLIENTSCRIPT),
            player.runWave,
            player.offerA,
            player.offerB,
            player.offerC,
            player.ownedModifiers,
            rewards.value(previous),
            rewards.value(future),
            rewards.value(earned),
        )
        ifSetEvents("$COMPONENT:left_loot", 0..3, IfEvent.Op1)
        ifSetEvents("$COMPONENT:right_loot", 0..3, IfEvent.Op1)
        ifSetEvents("$COMPONENT:tab_content_1_mod_list_content", 0..26, IfEvent.Op1)
        ifSetEvents("$COMPONENT:tab_content_2_content", 0..30, IfEvent.Op1)
    }

    fun ProtectedAccess.close() {
        ifClose()
    }

    fun ProtectedAccess.select(slot: Int) {
        player.selectedModifier = if (player.selectedModifier == slot) 0 else slot
    }

    fun ProtectedAccess.examine(inv: Inventory, comsub: Int) {
        val slot =
            inv.objs.withIndex().filter { it.value != null }.getOrNull(comsub)?.index ?: return
        objExamine(inv, slot)
    }

    companion object {
        const val INTERFACE = "interface.colosseum_intermission_2"
        const val COMPONENT = "component.colosseum_intermission_2"
        private const val INIT_SCRIPT = "clientscript.[clientscript,colosseum_intermission_2_init]"
    }
}
