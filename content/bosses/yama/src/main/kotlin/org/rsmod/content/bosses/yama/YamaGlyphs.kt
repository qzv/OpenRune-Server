package org.rsmod.content.bosses.yama

import org.rsmod.api.table.YamaGlyphCostsRow
import org.rsmod.api.table.YamaGlyphSlotsRow

internal data class GlyphSlot(val dx: Int, val dz: Int, val nearThrone: Boolean) {
    val centreDx: Int
        get() = dx + YamaGlyphs.SIZE / 2

    val centreDz: Int
        get() = dz + YamaGlyphs.SIZE / 2
}

internal enum class GlyphLayout(val nearThroneElement: YamaElement) {
    SHADOW_NEAR_THRONE(YamaElement.SHADOW),
    FIRE_NEAR_THRONE(YamaElement.FIRE),
    ;

    val opening: YamaElement
        get() = nearThroneElement.opposite

    fun elementAt(slot: GlyphSlot): YamaElement =
        if (slot.nearThrone) nearThroneElement else nearThroneElement.opposite
}

internal object YamaGlyphs {
    const val SIZE: Int = 3

    val SLOTS: List<GlyphSlot> by lazy {
        YamaGlyphSlotsRow.all().map { GlyphSlot(it.dx, it.dz, it.nearThrone) }
    }
}

internal object YamaGlyphCosts {
    private val COSTS: List<YamaGlyphCostsRow> by lazy {
        YamaGlyphCostsRow.all().sortedByDescending { it.minDistance }
    }

    fun range(element: YamaElement, yamaDistance: Int): IntRange {
        val cost = COSTS.first { yamaDistance >= it.minDistance }
        return if (element == YamaElement.FIRE) {
            cost.fireMin..cost.fireMax
        } else {
            cost.shadowMin..cost.shadowMax
        }
    }
}
