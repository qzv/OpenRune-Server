package org.rsmod.content.minigames.colosseum

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ColosseumMinimusTalk @Inject constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(ColosseumNpcs.LOBBY_MINIMUS) { startDialogue(it.npc) { talk() } }
    }

    private suspend fun Dialogue.talk() {
        chatPlayer(neutral, "Hello there.")
        chatNpc(neutral, "Welcome, ${title()}.")
        val topics = Topic.entries.toMutableList()
        val intro = !access.player.minimusIntroSeen
        if (intro) {
            introduction()
            access.player.minimusIntroSeen = true
        }
        var reworded = intro
        while (topics.isNotEmpty()) {
            val topic = pick(topics, reworded) ?: return
            reworded = true
            topics.remove(topic)
            when (topic) {
                Topic.Modifiers -> modifiers()
                Topic.Rewards -> rewards()
                Topic.Glory -> glory()
                Topic.Around -> around()
            }
        }
    }

    private fun Dialogue.title(): String = ColosseumRank.title(access.player.bestGlory)

    private suspend fun Dialogue.pick(topics: List<Topic>, reworded: Boolean): Topic? {
        val labels = topics.map { if (reworded) it.followUp else it.opening }
        val goodbye = null as Topic?
        val title = "Select an option"
        val choice =
            when (topics.size) {
                4 ->
                    choice5(
                        labels[0],
                        topics[0],
                        labels[1],
                        topics[1],
                        labels[2],
                        topics[2],
                        labels[3],
                        topics[3],
                        GOODBYE,
                        goodbye,
                        title = title,
                    )
                3 ->
                    choice4(
                        labels[0],
                        topics[0],
                        labels[1],
                        topics[1],
                        labels[2],
                        topics[2],
                        GOODBYE,
                        goodbye,
                        title = title,
                    )
                2 ->
                    choice3(
                        labels[0],
                        topics[0],
                        labels[1],
                        topics[1],
                        GOODBYE,
                        goodbye,
                        title = title,
                    )
                else -> choice2(labels[0], topics[0], GOODBYE, goodbye, title = title)
            }
        if (choice == null) {
            chatPlayer(neutral, GOODBYE)
        }
        return choice
    }

    private suspend fun Dialogue.introduction() {
        chatPlayer(neutral, "What is this place?")
        chatNpc(quiz, "This place...? Why, This is the great Fortis Colosseum!")
        chatNpc(happy, "Adventurers come from near and far to test their might within these walls.")
        chatPlayer(happy, "That sounds exciting!")
        chatNpc(
            happy,
            "Indeed it is. If you are victorious in the Colosseum, the whole of Varlamore shall " +
                "hear of your name.",
        )
        chatPlayer(happy, "I imagine it's quite the challenge then?")
        chatNpc(neutral, "It'll put your skills to the test alright.")
        chatPlayer(happy, "How do I participate?")
        chatNpc(
            quiz,
            "Just head through that tunnel behind me and we'll get the show started! Make sure " +
                "you've got everything you need. Once you're in you can't leave until the end of " +
                "a wave!",
        )
        chatNpc(neutral, "Come and ask if you have any other questions.")
    }

    private suspend fun Dialogue.modifiers() {
        chatPlayer(neutral, "Can you tell me about modifiers?")
        chatNpc(quiz, "Yes! After all, they were my idea...")
        chatPlayer(neutral, "Oh...")
        chatNpc(
            confused,
            "Every time you complete a wave, I'll offer you a choice of three modifiers.",
        )
        chatNpc(
            neutral,
            "Each modifier will increase the challenge for the rest of the run - So choose wisely!",
        )
        chatNpc(
            neutral,
            "Some modifiers can be offered multiple times, with the effects then growing in power!",
        )
        chatPlayer(neutral, "Thanks for the heads up!")
        chatNpc(happy, "Was there anything else?")
    }

    private suspend fun Dialogue.rewards() {
        chatPlayer(neutral, "How do I get rewarded?")
        chatNpc(
            quiz,
            "Well it's a bit different to just killing monsters and picking up items, as I'm " +
                "sure you're used to...",
        )
        chatPlayer(neutral, "How so?")
        chatNpc(
            quiz,
            "After each wave inside the Colosseum, I'll show you the rewards you have earned, as " +
                "well as what you'll earn from the next wave...",
        )
        chatNpc(
            neutral,
            "If you aren't confident that you can complete the next wave, you can claim your " +
                "existing rewards and exit early.",
        )
        chatNpc(
            neutral,
            "However, if you're confident you can beat the next wave, you can risk your whole " +
                "reward pile to get even more!",
        )
        chatNpc(
            happy,
            "Be careful though. If you die or leave the arena before claiming your loot from me, " +
                "you will lose all of the rewards you've earned.",
        )
        chatPlayer(happy, "So I'm guessing the higher the wave, the better the rewards?")
        chatNpc(neutral, "Oh, you're good. Exactly that!")
        chatNpc(
            happy,
            "Even better - if you manage to finish all of the waves and the final challenge, " +
                "then you'll be awarded with Dizana's Quiver.",
        )
        chatNpc(
            happy,
            "It's a handy piece of kit that you wear on your back to store extra bolts or arrows.",
        )
        chatPlayer(neutral, "That does sound handy! Thanks for explaining all of that.")
        chatNpc(happy, "Anytime, ${title()}. Anything else?")
    }

    private suspend fun Dialogue.glory() {
        chatPlayer(happy, "What is Glory?")
        chatNpc(quiz, "It's the measure of your progress inside the Colosseum.")
        chatNpc(happy, "Speak to Gloria over there if you want to know the details.")
        chatPlayer(happy, "Thanks.")
    }

    private suspend fun Dialogue.around() {
        chatPlayer(neutral, "What do you do around here?")
        chatNpc(
            quiz,
            "I'm Minimus. It's not my real name of course, but it seems to have stuck. I started " +
                "out competing here as a gladiator when I was a young'n. After that I became one " +
                "of the lanistas.",
        )
        chatPlayer(neutral, "Lanistas?")
        chatNpc(
            quiz,
            "The ones responsible for overseeing the selecting and training of the gladiators.",
        )
        chatNpc(
            neutral,
            "Eventually, the kingdom gave me the role of Colosseum Master. I still make sure to " +
                "fit some fighting in, but now I have the honour of managing the fights " +
                "themselves.",
        )
        chatPlayer(neutral, "So are you not training like the rest of the gladiators?")
        chatNpc(
            quiz,
            "I have no need! I'm already one of the greatest fighters to grace this arena.",
        )
        chatPlayer(bored, "Are you at a disadvantage because of your height?")
        chatNpc(
            quiz,
            "No! How dare you even suggest that! Those spindly human bodies are unfit to go toe " +
                "to toe with a fine dwarf like myself!",
        )
        chatNpc(angry, "Now, did you need anything else?")
    }

    private enum class Topic(val opening: String, val followUp: String) {
        Modifiers("Can we talk about modifiers?", "Can you tell me about modifiers?"),
        Rewards("How can I get rewards?", "How do I get rewarded?"),
        Glory("What is Glory?", "What is Glory?"),
        Around("What do you do around here?", "What do you do around here?"),
    }

    private companion object {
        const val GOODBYE = "Goodbye."
    }
}
