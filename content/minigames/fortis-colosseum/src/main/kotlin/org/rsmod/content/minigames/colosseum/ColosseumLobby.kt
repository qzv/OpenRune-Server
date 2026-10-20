package org.rsmod.content.minigames.colosseum

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.other.pets.PetRewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
class QuiverTrade
@Inject
constructor(private val pets: PetRewards, private val random: GameRandom) {
    fun canGamble(player: Player): Boolean = !pets.owns(player, SolPet.obj)

    fun tradeForSplinters(player: Player, invSlot: Int): Boolean =
        player
            .invTransaction(player.inv) {
                val inv = select(player.inv)
                delete {
                    from = inv
                    obj = QUIVER.asRSCM(RSCMType.OBJ)
                    strictCount = 1
                    strictSlot = invSlot
                }
                insert {
                    into = inv
                    obj = SPLINTERS.asRSCM(RSCMType.OBJ)
                    strictCount = SPLINTER_COUNT
                }
            }
            .success

    fun gamble(player: Player, invSlot: Int): Boolean? {
        val taken =
            player
                .invTransaction(player.inv) {
                    val inv = select(player.inv)
                    delete {
                        from = inv
                        obj = QUIVER.asRSCM(RSCMType.OBJ)
                        strictCount = 1
                        strictSlot = invSlot
                    }
                }
                .success
        if (!taken) return null
        if (random.of(SolPet.rate) != 0) return false
        pets.give(player, SolPet.obj)
        return true
    }

    companion object {
        const val QUIVER = "obj.dizanas_quiver_uncharged"
        const val SPLINTERS = "obj.sunfiresplinter"
        const val SPLINTER_COUNT = 4000
    }
}

class ColosseumLobby @Inject constructor(private val trade: QuiverTrade) : PluginScript() {
    override fun ScriptContext.startup() {
        val minimus =
            checkNotNull(
                ServerCacheManager.getNpc(ColosseumNpcs.LOBBY_MINIMUS.asRSCM(RSCMType.NPC))
            )
        onOpNpcU(minimus, obj(QuiverTrade.QUIVER)) {
            startDialogue(it.npc) { tradeQuiver(it.invSlot) }
        }
        for (other in OTHER_QUIVERS) {
            onOpNpcU(minimus, obj(other)) {
                startDialogue(it.npc) { chatNpc(sad, UNCHARGED_ONLY_LINE) }
            }
        }
    }

    private fun Dialogue.keepLine() =
        "Very well, ${ColosseumRank.title(player.bestGlory)}. Keep your trophy."

    private suspend fun Dialogue.tradeQuiver(invSlot: Int) {
        chatPlayer(quiz, "I have a spare Dizana's quiver.")
        chatNpc(
            neutral,
            "Want to offer it for some sunfire splinters? Or maybe I could also try to convince " +
                "Smol Heredit to follow you. Just be aware you won't get the quiver back.",
        )
        val choice =
            if (trade.canGamble(access.player)) {
                choice3(
                    SPLINTERS_OPTION,
                    SPLINTERS,
                    "Yes, gamble it for Smol Heredit.",
                    GAMBLE,
                    KEEP_OPTION,
                    KEEP,
                    title = TRADE_TITLE,
                )
            } else {
                choice2(SPLINTERS_OPTION, SPLINTERS, KEEP_OPTION, KEEP, title = TRADE_TITLE)
            }
        when (choice) {
            SPLINTERS -> confirmSplinters(invSlot)
            GAMBLE -> gamble(invSlot)
            else -> chatNpc(neutral, keepLine())
        }
    }

    private suspend fun Dialogue.confirmSplinters(invSlot: Int) {
        val confirmed =
            choice2(
                KEEP_QUIVER_OPTION,
                false,
                "Yes, exchange Dizana's quiver for 4,000 sunfire splinters.",
                true,
                title = CONFIRM_TITLE,
            )
        if (confirmed) trade.tradeForSplinters(access.player, invSlot)
        else chatNpc(neutral, keepLine())
    }

    private suspend fun Dialogue.gamble(invSlot: Int) {
        val confirmed =
            choice2(
                KEEP_QUIVER_OPTION,
                false,
                "Yes, gamble for Smol Heredit.",
                true,
                title = CONFIRM_TITLE,
            )
        if (!confirmed) {
            chatNpc(neutral, keepLine())
            return
        }
        if (trade.gamble(access.player, invSlot) == false) {
            chatNpc(neutral, "He doesn't want to go with you. Sorry.")
        }
    }

    private fun obj(name: String) =
        checkNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ)))

    private companion object {
        private val OTHER_QUIVERS =
            listOf("obj.dizanas_quiver_charged", "obj.dizanas_quiver_infinite")
        private const val UNCHARGED_ONLY_LINE =
            "I'm afraid you can only offer spare uncharged versions of the quiver to me."
        private const val TRADE_TITLE = "Trade Dizana's quiver?"
        private const val CONFIRM_TITLE = "Are you REALLY sure?"
        private const val SPLINTERS_OPTION = "Yes, trade it for 4,000 sunfire splinters."
        private const val KEEP_OPTION = "No, keep it."
        private const val KEEP_QUIVER_OPTION = "No, keep Dizana's quiver."

        private const val SPLINTERS = 1
        private const val GAMBLE = 2
        private const val KEEP = 3
    }
}
