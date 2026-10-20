package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.util.EnumSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.combat.formulas.attributes.CombatNpcAttributes
import org.rsmod.api.combat.formulas.attributes.CombatSpellAttributes
import org.rsmod.api.combat.formulas.maxhit.magic.MagicMaxHitOperations
import org.rsmod.api.config.refs.params

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class YamaDemonbaneTest {
    private val markedStaff =
        EnumSet.of(
            CombatSpellAttributes.Demonbane,
            CombatSpellAttributes.MarkOfDarkness,
            CombatSpellAttributes.PurgingStaff,
        )

    private fun percent(npc: String): Int =
        ServerCacheManager.getNpc(npc.asRSCM(RSCMType.NPC))!!.param(params.demonbane_effectiveness)

    private fun range(npc: String, modifiedDamage: Int): IntRange =
        MagicMaxHitOperations.modifySpellDamageRange(
            modifiedDamage = modifiedDamage,
            baseDamage = modifiedDamage,
            attackRate = 5,
            targetWeaknessPercent = 0,
            spellAttributes = markedStaff,
            npcAttributes = EnumSet.of(CombatNpcAttributes.Demon),
            demonbanePercent = percent(npc),
        )

    @Test
    fun `a marked purging staff cast one shots a half health void flare`() {
        val flareHitpoints = 71
        assertTrue(range("npc.yama_voidflare", modifiedDamage = 36).last >= flareHitpoints)
    }

    @Test
    fun `the same cast against yama does not reach the flare threshold`() {
        assertEquals(0..57, range("npc.yama", modifiedDamage = 36))
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
        }
    }
}
