package org.rsmod.content.bosses.yama

import org.rsmod.api.random.GameRandom
import org.rsmod.api.table.YamaLinesRow

internal enum class YamaLineGroup(val key: String) {
    GREETING("greeting"),
    THRONE("throne"),
    AGGRO("aggro"),
    TRANSITION("transition"),
    METEOR("meteor"),
    SHADOW("shadow"),
}

internal object YamaLines {
    private val GROUPS: Map<String, List<YamaLinesRow>> by lazy {
        YamaLinesRow.all().groupBy { it.group }
    }

    fun pick(group: YamaLineGroup, random: GameRandom): String? {
        val lines = GROUPS[group.key].orEmpty()
        var remaining = random.of(lines.sumOf { it.weight })
        for (line in lines) {
            if (remaining < line.weight) return line.text
            remaining -= line.weight
        }
        return null
    }
}
