package org.rsmod.api.bosses.runtime

import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.dsl.resetAnim
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

class BossEncounterEntryOnAttackTest {
    private val spec =
        boss("npc.boss") {
            stats(attackRate = 8)
            val standard = ability("standard", resetAnim())
            phase("main") { weightedSelectorRandom { +random(standard, weight = 1) } }
            phase("instant", entryHp = 0.5) {}
            phase("slotted", entryHp = 0.25, entryOnAttack = true) {}
        }

    @Test
    fun `a default hp entry is ready on any tick`() {
        val encounter = encounter()
        encounter.startAttack("standard", tick = 10)
        assertTrue(encounter.entryReady(spec.phases.getValue("instant"), 11))
    }

    @Test
    fun `an entry on attack waits for the next attack slot`() {
        val encounter = encounter()
        encounter.startAttack("standard", tick = 10)
        val phase = spec.phases.getValue("slotted")
        assertFalse(encounter.entryReady(phase, 11))
        assertFalse(encounter.entryReady(phase, 17))
        assertTrue(encounter.entryReady(phase, 18))
    }

    @Test
    fun `an entry on attack waits while the encounter is busy`() {
        val encounter = encounter()
        encounter.startAttack("standard", tick = 10)
        encounter.busyUntil = 20
        val phase = spec.phases.getValue("slotted")
        assertFalse(encounter.entryReady(phase, 18))
        assertTrue(encounter.entryReady(phase, 20))
    }

    private fun encounter(): BossEncounter {
        val type = NpcServerType(id = 1, name = "Boss", size = 1, hitpoints = 100)
        return BossEncounter(Npc(type, CoordGrid(0, 1, 1, 0, 0)), spec, MapClock())
    }
}
