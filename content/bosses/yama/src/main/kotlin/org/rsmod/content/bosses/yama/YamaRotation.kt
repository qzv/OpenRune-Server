package org.rsmod.content.bosses.yama

import dev.openrune.types.ObjectServerType
import org.rsmod.api.table.YamaElementsRow

internal enum class YamaElement(private val dbrow: String, val immunitySpotanim: String) {
    FIRE("dbrow.yama_element_fire", "spotanim.vfx_yama_fire_immunity"),
    SHADOW("dbrow.yama_element_shadow", "spotanim.vfx_yama_shadow_immunity");

    private val row: YamaElementsRow by lazy { YamaElementsRow.getRow(dbrow) }

    val opposite: YamaElement
        get() = if (this == FIRE) SHADOW else FIRE

    val glyphName: String
        get() = row.glyphName

    val textColour: String
        get() = row.textColour

    val glyphLoc: ObjectServerType
        get() = row.glyphLoc

    val deactivateLoc: ObjectServerType
        get() = row.deactivateLoc

    val flareModels: List<Int>
        get() = row.flareModels

    fun colour(text: String): String = textColour + text

    fun absorbMessage(): String = colour("The $glyphName absorbs the damage from the attack.")
}

internal enum class YamaAttackKind {
    STANDARD,
    SPECIAL,
}

internal data class YamaAttack(val kind: YamaAttackKind, val element: YamaElement)

internal object YamaRotation {
    const val STANDARDS_PER_SPECIAL: Int = 3
    const val NO_FLARE: Int = -1

    fun attack(
        opening: YamaElement,
        index: Int,
        standardsPerSpecial: Int = STANDARDS_PER_SPECIAL,
        flareIndex: Int = NO_FLARE,
    ): YamaAttack {
        val cycleLength = standardsPerSpecial + 1
        require(index >= 0) { "index must not be negative: $index" }
        if (index == 0) return YamaAttack(YamaAttackKind.STANDARD, opening)
        val cycle = (index - 1) / cycleLength
        val element = if (cycle % 2 == 0) opening.opposite else opening
        val kind =
            if ((index - 1) % cycleLength == 0) YamaAttackKind.SPECIAL else YamaAttackKind.STANDARD
        val afterFlare =
            flareIndex != NO_FLARE && index in flareIndex + 1..flareIndex + standardsPerSpecial
        return YamaAttack(kind, if (afterFlare) element.opposite else element)
    }
}
