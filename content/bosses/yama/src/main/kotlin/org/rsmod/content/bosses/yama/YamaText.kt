package org.rsmod.content.bosses.yama

import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

internal object YamaText {
    const val ABSORB_PREFIX = "You absorb a "
    const val FADE_COLOUR = "<col=ff3045>"
    const val FIRE_CONJURE = "Yama conjures a fire-based attack at you."
    const val INJURED = "You've been injured and can't use protection prayers!"
}

internal object YamaPhases {
    const val FINAL = 3
    const val FINAL_NAME = "three"
    const val TRANSITION_DAMAGE_SCALE = 0.5
    val SPAWN: CoordGrid = CoordGrid(1501, 10076)

    fun footprintDistance(yama: Npc, coords: CoordGrid): Int {
        val far = yama.size - 1
        val dx = maxOf(0, coords.x - (yama.coords.x + far), yama.coords.x - coords.x)
        val dz = maxOf(0, coords.z - (yama.coords.z + far), yama.coords.z - coords.z)
        return maxOf(dx, dz)
    }
}
