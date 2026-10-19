package org.rsmod.content.bosses.yama.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object YamaLinesTable {
    const val GROUP = 0
    const val TEXT = 1
    const val WEIGHT = 2

    const val GREETING = "greeting"
    const val THRONE = "throne"
    const val AGGRO = "aggro"
    const val TRANSITION = "transition"
    const val METEOR = "meteor"
    const val SHADOW = "shadow"

    private class Line(val group: String, val text: String?, val weight: Int = 1)

    private val LINES =
        listOf(
            Line(
                GREETING,
                "<p=4>Back again I see. I can see how you would find these bouts exhilarating, " +
                    "being such a feeble opponent.",
                31,
            ),
            Line(
                GREETING,
                "<p=2>A victory earned. But there is room for improvement, is there not?",
                4,
            ),
            Line(GREETING, "<p=2>It is satisfying to taste revenge, is it not?", 3),
            Line(GREETING, "<p=2>Your victories mount. You are getting better at this.", 2),
            Line(GREETING, "<p=2>You return once more. Excellent.", 2),
            Line(GREETING, "<p=2>You look happy with yourself. How endearing.", 1),
            Line(
                GREETING,
                "<p=2>I must once again commend your strategy. Employing tactics so boring they " +
                    "put your opponents to sleep is very novel.",
                1,
            ),
            Line(
                GREETING,
                "<p=2>I hope you are finding the spoils from my treasury to your liking. There " +
                    "are plenty more for you to claim.",
                1,
            ),
            Line(GREETING, null, 7),
            Line(THRONE, "A satisfying warmup. Collect your loot and let us continue.", 18),
            Line(THRONE, "Fair is fair.", 15),
            Line(THRONE, "Your reward, as agreed.", 12),
            Line(THRONE, "Effective, if a bit cowardly.", 9),
            Line(
                THRONE,
                "Tired? I'm not surprised. You lack the constitution to do better.",
                6,
            ),
            Line(THRONE, "Risk averse, are we?", 6),
            Line(THRONE, "A passing performance.", 4),
            Line(THRONE, "Better than average, but still lacking any real talent.", 2),
            Line(THRONE, "A chance at greatness, wasted on an overly cautious coward.", 2),
            Line(THRONE, "A good risk, adequately rewarded.", 1),
            Line(AGGRO, "Your strike lacks bite."),
            Line(AGGRO, "A change of pace."),
            Line(TRANSITION, "Begone"),
            Line(TRANSITION, "Enough."),
            Line(TRANSITION, "You bore me."),
            Line(METEOR, "Ven, estella infernus"),
            Line(METEOR, "Colabesur infernus"),
            Line(METEOR, "Colabi, infernus"),
            Line(SHADOW, "Ven, umbra eclipta"),
            Line(SHADOW, "Umbra, proriumpse"),
            Line(SHADOW, "Umbra apprerendehe"),
        )

    fun lines() =
        dbTable("dbtable.yama_lines", serverOnly = true) {
            column("group", GROUP, VarType.STRING)
            column("text", TEXT, VarType.STRING)
            column("weight", WEIGHT, VarType.INT)
            val counts = HashMap<String, Int>()
            for (line in LINES) {
                val index = counts.merge(line.group, 1, Int::plus)
                row("dbrow.yama_line_${line.group}_$index") {
                    column(GROUP, line.group)
                    line.text?.let { column(TEXT, it) }
                    column(WEIGHT, line.weight)
                }
            }
        }
}
