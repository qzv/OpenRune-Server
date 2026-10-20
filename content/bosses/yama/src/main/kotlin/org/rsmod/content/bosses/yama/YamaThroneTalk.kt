package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfSubType
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.ironman.isAnyIronman
import org.rsmod.api.player.protect.ProtectedAccess

internal object YamaThroneTalk {
    const val SIGN_STATE_NONE = 0
    const val SIGN_STATE_BACKED_OUT = 1
    const val SIGN_STATE_SIGNED = 2
    private const val SELECT = "Select an option"
    private const val OATHPLATE_TITLE = "Which piece of oathplate would you like to receive?"
    private const val EMPTY_LABELS = "|||||||"
    private const val CONTRACT_WARNING =
        "<col=ff0000>Are you sure?</col> A contract with Yama cannot be backed out of."
    private const val SKILLMULTI = "interface.skillmulti"
    private const val CHATBOX_MODAL = "component.chatbox:chatmodal"
    private const val CHATBOX_UNCLAMP = "varbit.chatmodal_unclamp"
    private const val CHATBOX_RESET_BACKGROUND_SCRIPT =
        "clientscript.[clientscript,toplevel_chatbox_resetbackground]"
    private const val SKILLMULTI_SETUP_SCRIPT = "clientscript.[clientscript,skillmulti_setup]"
    private const val SKILLMULTI_ACTION = 33
    private const val SKILLMULTI_ITEM_SLOTS = 18
    private val SKILLMULTI_SUBS = 0..1
    private val SKILLMULTI_BUTTONS =
        listOf("component.skillmulti:a", "component.skillmulti:b", "component.skillmulti:c")

    suspend fun Dialogue.talk(
        name: String,
        state: Int,
        onDecline: () -> Unit,
        onSign: () -> Unit,
        onChallenge: () -> Unit,
    ) {
        when (state) {
            SIGN_STATE_NONE -> {
                firstMeeting(name, onDecline, onSign)
                return
            }
            SIGN_STATE_BACKED_OUT -> {
                chatNpc(
                    neutral,
                    "You are back. Excellent. I have little time for cowards. If you are ready, " +
                        "I will retrieve the contract.",
                )
                contractPrompt(name, onDecline, onSign)
                return
            }
        }
        chatNpc(happy, "Welcome, $name. Have you come to challenge me?")
        when (
            choice3(
                "Yes. Let's do this.",
                Menu.CHALLENGE,
                "Actually, I have some questions.",
                Menu.QUESTIONS,
                "No, I'd rather not.",
                Menu.DECLINE,
                title = SELECT,
            )
        ) {
            Menu.CHALLENGE -> {
                chatPlayer(angry, "Yes. Let's do this.")
                onChallenge()
            }
            Menu.QUESTIONS -> questions()
            Menu.DECLINE -> {
                chatPlayer(neutral, "No, I'd rather not.")
                chatNpc(
                    neutral,
                    "I see. Perhaps you are just here to waste my time then? Return when you are " +
                        "not.",
                )
            }
        }
    }

    suspend fun Dialogue.offerContract(name: String, onDecline: () -> Unit, onSign: () -> Unit) {
        chatNpc(
            angry,
            "Ah... Yes, let us not get bogged down in the minutiae. Before we start, a quick " +
                "contract. Nothing but a formality.",
        )
        contractPrompt(name, onDecline, onSign)
    }

    suspend fun Dialogue.contractOffer(contract: YamaContract, onSign: (String?) -> Unit) {
        chatNpc(happy, "Why... Do my eyes deceive me? I see a glimmer of courage within you.")
        when (
            choice3(
                "I know what I'm doing, sign the contract.",
                ContractMenu.SIGN,
                "I want to know more about this contract.",
                ContractMenu.MORE,
                "Er... No, I thought you'd lost this.",
                ContractMenu.LEAVE,
                title = SELECT,
            )
        ) {
            ContractMenu.SIGN -> {
                chatPlayer(happy, "I know what I'm doing, sign the contract.")
                chatNpc(happy, "With pleasure.")
                signContract(contract, onSign, "On second thought, I think I'll hold off.") {
                    chatNpc(
                        neutral,
                        "Do not make a habit of wasting my time like this. Get out of my sight.",
                    )
                }
            }
            ContractMenu.MORE -> {
                chatPlayer(neutral, "I want to know more about this contract.")
                chatNpc(
                    neutral,
                    "Signing these contracts agrees us to a duel in which I am less inhibited. " +
                        "My powers will be greater than what you have experienced before.",
                )
                chatNpc(
                    neutral,
                    "Each one details unique rules and rewards, the details of which you can " +
                        "read on the contract itself.",
                )
                chatNpc(happy, "But I'm sure you already have. Shall we?")
                objbox(contract.item, "Yama presses a quill firmly into your hands.")
                val readAgain = "No, I think I'd like to read over the terms again."
                signContract(contract, onSign, readAgain) {
                    chatNpc(neutral, "Very well. Return when you are ready.")
                }
            }
            ContractMenu.LEAVE -> {
                chatPlayer(neutral, "Er... No, I thought you'd lost this.")
                chatNpc(
                    neutral,
                    "No, that contract was allowed to come into your possession quite " +
                        "deliberately. And I see you have bravely decided to make use of it.",
                )
                chatPlayer(neutral, "I'm not sure I...")
                chatNpc(
                    neutral,
                    "I see that I had misjudged you, ${access.player.displayName}. Let us sign " +
                        "the contract and cement that assessment.",
                )
                objbox(contract.item, "Yama presses a quill firmly into your hands.")
                signContract(
                    contract,
                    onSign,
                    "No, I think it might have been somebody else that dropped it. I should " +
                        "really go and find them...",
                ) {
                    chatNpc(
                        neutral,
                        "Yes, perhaps it was. Perhaps it belonged to somebody else. Somebody " +
                            "with a semblance of pride or self respect.",
                    )
                    chatNpc(neutral, "If you find them, be sure to send them my way.")
                }
            }
        }
    }

    private suspend fun Dialogue.signContract(
        contract: YamaContract,
        onSign: (String?) -> Unit,
        declineLine: String,
        onDecline: suspend Dialogue.() -> Unit,
    ) {
        if (!choice2("Yes.", true, "No.", false, title = "Sign the contract?")) {
            chatPlayer(neutral, declineLine)
            onDecline()
            return
        }
        var piece: String? = null
        if (contract.pieceChoice) {
            piece = pickOathplate() ?: return
        }
        onSign(piece)
    }

    private suspend fun Dialogue.pickOathplate(): String? {
        chatNpc(
            neutral,
            "The terms of this contract dictate that you are to make your choice of oathplate to " +
                "receive known prior to the fight. Sign it here.",
        )
        val piece = access.pickOathplatePiece() ?: return null
        objbox(piece.item, "You picked ${piece.label}, are you sure?")
        val confirmed =
            choice2(
                "No, I've changed my mind.",
                false,
                "Yes, I want the ${piece.option}.",
                true,
                title = SELECT,
            )
        return if (confirmed) piece.item else null
    }

    private suspend fun ProtectedAccess.pickOathplatePiece(): OathplatePiece? {
        val pieces = OATHPLATE_PIECES
        vars[CHATBOX_UNCLAMP] = 1
        runClientScript(CHATBOX_RESET_BACKGROUND_SCRIPT.asRSCM(RSCMType.CLIENTSCRIPT))
        ifOpenSub(SKILLMULTI, CHATBOX_MODAL, IfSubType.Modal)
        val options = listOf(OATHPLATE_TITLE) + pieces.map { it.option }
        val labels = options.joinToString("|") + EMPTY_LABELS
        val emptySlots = List(SKILLMULTI_ITEM_SLOTS - pieces.size) { -1 }
        val items = pieces.map { it.item.asRSCM(RSCMType.OBJ) } + emptySlots
        val setup = SKILLMULTI_SETUP_SCRIPT.asRSCM(RSCMType.CLIENTSCRIPT)
        runClientScript(setup, SKILLMULTI_ACTION, labels, 1, *items.toTypedArray(), 1)
        for (button in SKILLMULTI_BUTTONS.take(pieces.size)) {
            ifSetEvents(button, SKILLMULTI_SUBS, IfEvent.PauseButton)
        }
        val input = coroutine.pause(ResumePauseButtonInput::class)
        return pieces.getOrNull(SKILLMULTI_BUTTONS.indexOf(input.component))
    }

    private suspend fun Dialogue.firstMeeting(
        name: String,
        onDecline: () -> Unit,
        onSign: () -> Unit,
    ) {
        chatNpc(
            neutral,
            "Ah... $name, welcome. I would offer you some water, but we don't get much of it " +
                "down here.",
        )
        chatPlayer(neutral, "Thanks...")
        chatNpc(neutral, "I take it you have questions.")
        when (
            choice3(
                "You know why I'm here. Stop messing around.",
                Opening.STOP_MESSING,
                "I take it you have answers.",
                Opening.ANSWERS,
                "Yes, a few of them actually.",
                Opening.QUESTIONS,
                title = SELECT,
            )
        ) {
            Opening.STOP_MESSING -> {
                chatNpc(
                    neutral,
                    "I do, but it is important that it is you who is asking for information, not " +
                        "me that is offering it.",
                )
                chatPlayer(neutral, "Okay... How are you still communicating with your followers?")
                chatNpc(neutral, "Why, through my voice, of course.")
            }
            Opening.ANSWERS -> {
                chatPlayer(neutral, "I take it you have answers.")
                chatNpc(neutral, "I do, but I rarely share them umprompted.")
                chatPlayer(neutral, "Okay, how are you still communicating with your followers?")
                chatNpc(neutral, "Why, through my voice, of course.")
            }
            Opening.QUESTIONS -> {
                chatPlayer(
                    neutral,
                    "Yes, a few of them actually. How are you still communicating with your " +
                        "followers?",
                )
                chatNpc(neutral, "Why, through my voice, of course.")
            }
        }
        explanation()
        contractOffer(name, onDecline, onSign)
    }

    private suspend fun Dialogue.explanation() {
        chatPlayer(
            neutral,
            "You know what I mean. How are you still communicating with your voice?",
        )
        chatNpc(neutral, "The same way I always have.")
        chatPlayer(
            neutral,
            "But the dark nullifier... I thought it was supposed to sever your connection.",
        )
        chatNpc(
            neutral,
            "Hmm... Yes, if anything could have done so, it would have been the power of the " +
                "Dark Altar.",
        )
        chatNpc(
            neutral,
            "However, when your opponents have only one angle of approach, it makes them so " +
                "predictable.",
        )
        chatPlayer(neutral, "You knew we would try to use it?")
        chatNpc(
            neutral,
            "Yes. When I heard you would be hand delivering me a connection to the Dark Altar... " +
                "Why, I was overjoyed.",
        )
        chatPlayer(neutral, "But... I remember planting it! The plan went off without a hitch!")
        chatNpc(neutral, "I do not doubt that you remember it that way.")
        chatPlayer(angry, "What did you do?!")
        chatNpc(
            neutral,
            "A simple transfiguration of the mind. My disciples granted you a memory of success. " +
                "A fitting payment for your gratious service.",
        )
        chatPlayer(angry, "You modified my memory?!")
        chatNpc(
            neutral,
            "Yes. And I have now answered five of your questions. I believe my generosity has " +
                "been both demonstrated and exhausted.",
        )
        chatPlayer(
            angry,
            "You call that gloating generosity? If you didn't want something from me, I wouldn't " +
                "still be down here. Get to the point.",
        )
    }

    private suspend fun Dialogue.contractOffer(
        name: String,
        onDecline: () -> Unit,
        onSign: () -> Unit,
    ) {
        chatNpc(
            neutral,
            "Hmm... Perhaps you are more astute than I thought. Very well, there is something " +
                "that I want.",
        )
        chatPlayer(angry, "Well you won't be getting it from me!")
        chatNpc(
            neutral,
            "I am Yama. Every mortal desires something of mine. Ancient armaments? Hidden " +
                "knowledge? Or perhaps a taste of revenge...",
        )
        chatNpc(neutral, "These things could be yours.")
        chatNpc(
            neutral,
            "Face me in combat. If you win, you take it all. Armaments, knowledge, revenge.",
        )
        chatPlayer(neutral, "And if I lose?")
        chatNpc(neutral, "I take your soul. For all of eternity.")
        chatPlayer(neutral, "No thanks.")
        chatNpc(neutral, "I see. Perhaps we could lower the stakes then.")
        chatNpc(
            neutral,
            "I will face you in an inhibited form. I'll even allow you to bring an ally, " +
                "provided they have entered the same contract.",
        )
        chatNpc(
            neutral,
            "If you win, I will give you a chance to gain some of my treasures, and of course, " +
                "you will have sampled your revenge.",
        )
        chatPlayer(neutral, "And if I lose?")
        chatNpc(
            neutral,
            "When you take your final rest, I shall take your soul for a year. A drop in the " +
                "ocean compared to eternity.",
        )
        chatPlayer(neutral, "How can I know you'll keep your word?")
        chatNpc(
            neutral,
            "The same way I will know you shall keep yours. A binding contract. As luck would " +
                "have it, I had one of my scribes draft one up for just such an occasion.",
        )
        chatPlayer(neutral, "...Okay.")
        contractPrompt(name, onDecline, onSign)
    }

    private suspend fun Dialogue.contractPrompt(
        name: String,
        onDecline: () -> Unit,
        onSign: () -> Unit,
    ) {
        chatNpc(happy, "Sign right here.")
        val sign = choice2("Yes.", true, "No.", false, title = "Sign the contract?")
        if (!sign) {
            onDecline()
            declineContract()
            return
        }
        val sure =
            choice2(
                "Yes.",
                true,
                "No.",
                false,
                title = CONTRACT_WARNING,
            )
        if (!sure) {
            onDecline()
            declineContract()
            return
        }
        onSign()
        chatNpc(
            happy,
            "Excellent... Challenge me when you are ready, $name. I very much look forward to it.",
        )
    }

    private suspend fun Dialogue.declineContract() {
        chatPlayer(neutral, "I'm not sure I want to...")
        chatNpc(
            neutral,
            "Of course. There is no rush. Leave, with your tail tucked between your legs. If you " +
                "grow a spine and wish to fulfil your revenge, I will be waiting.",
        )
    }

    private suspend fun Dialogue.questions() {
        chatPlayer(neutral, "Actually, I have some questions.")
        chatNpc(neutral, "Of course.")
        while (true) {
            if (!question()) return
        }
    }

    private suspend fun Dialogue.question(): Boolean {
        when (
            choice3(
                "There's something in our contract about additional contracts...",
                Question.CONTRACTS,
                "Do you keep any record of our fights?",
                Question.RECORDS,
                "That's all.",
                Question.DONE,
                title = SELECT,
            )
        ) {
            Question.CONTRACTS -> additionalContracts()
            Question.RECORDS -> {
                chatPlayer(neutral, "Do you keep any record of our fights?")
                chatNpc(neutral, "I do, I can have somebody retrieve your file. One moment.")
                mesbox("A mail-imp holding a scroll appears. Yama smites the imp.")
                chatNpc(neutral, "For your reading pleasure.")
            }
            Question.DONE -> {
                chatPlayer(neutral, "That's all.")
                return false
            }
        }
        return true
    }

    private suspend fun Dialogue.additionalContracts() {
        chatPlayer(
            neutral,
            "There's something in our contract about additional contracts... Why would I want to " +
                "sign any more of them?",
        )
        chatNpc(
            neutral,
            "As outlined in subsection C, if you wish for your revenge to feel more 'real', I am " +
                "willing to lift some of the restrictions placed by the original contract.",
        )
        chatPlayer(neutral, "Why would I want that?")
        if (access.player.isAnyIronman) {
            chatNpc(
                neutral,
                "You seem to enjoy self-imposed difficulty. I thought I would indulge you.",
            )
        } else {
            chatNpc(
                neutral,
                "You may not, in which case you can sell them to someone with greater respect " +
                    "for themselves. But if you do, the option is there.",
            )
        }
        chatNpc(
            neutral,
            "And if you manage to claim victory, there is of course a reward for each contract.",
        )
        chatPlayer(neutral, "Okay, can I see the contracts?")
        chatNpc(
            neutral,
            "No. For this, I require from you a service. My demons are... Lacking, of late. " +
                "Unrefined.",
        )
        chatNpc(
            neutral,
            "They must fight, suffer and reform. If you deliver them specific ends during your " +
                "'tasks' in my domain, they may leave behind a contract.",
        )
        chatNpc(neutral, "And of course, if you defeat me, I may also reward you with one.")
        chatPlayer(neutral, "What kinds of 'ends'?")
        chatNpc(
            neutral,
            "Death by two-handed melee weapons, heavy ranged weapons, magic spells, special " +
                "attacks, and defeating them whilst bound. Each kind may award a different " +
                "contract.",
        )
        chatNpc(
            neutral,
            "If you find yourself eager to obtain any contract, I will use a glyph to indicate " +
                "my preferred method, which you can utilise for a better chance to receive that " +
                "contract.",
        )
        chatPlayer(neutral, "What kinds of contracts can I obtain?")
        chatNpc(
            neutral,
            "Were you more adept at reading you would find the details in our original contract. " +
                "Otherwise, bring any you obtain to me and I shall explain then.",
        )
    }

    private enum class ContractMenu {
        SIGN,
        MORE,
        LEAVE,
    }

    private class OathplatePiece(val item: String) {
        val option: String
            get() = ServerCacheManager.getItem(item.asRSCM(RSCMType.OBJ))?.name.orEmpty()

        val label: String
            get() = option.split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }

    private val OATHPLATE_PIECES =
        listOf("obj.oathplate_helm", "obj.oathplate_chest", "obj.oathplate_legs")
            .map(::OathplatePiece)

    private enum class Menu {
        CHALLENGE,
        QUESTIONS,
        DECLINE,
    }

    private enum class Opening {
        STOP_MESSING,
        ANSWERS,
        QUESTIONS,
    }

    private enum class Question {
        CONTRACTS,
        RECORDS,
        DONE,
    }
}
