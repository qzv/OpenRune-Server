package org.rsmod.content.bosses.yama

import dev.openrune.TypedParamType
import org.rsmod.api.config.refs.params
import org.rsmod.api.table.YamaDefencesRow

internal object YamaDefences {
    fun of(finalPhase: Boolean, meleeTarget: Boolean): Map<TypedParamType<Int>, Int> =
        when {
            finalPhase -> FINAL_PHASE
            meleeTarget -> MELEE_TARGET
            else -> emptyMap()
        }

    private val FINAL_PHASE by lazy { overrides("dbrow.yama_defences_final_phase") }

    private val MELEE_TARGET by lazy { overrides("dbrow.yama_defences_melee_target") }

    private fun overrides(dbrow: String): Map<TypedParamType<Int>, Int> {
        val row = YamaDefencesRow.getRow(dbrow)
        return listOfNotNull(
                row.stab?.let { params.defence_stab to it },
                row.slash?.let { params.defence_slash to it },
                row.crush?.let { params.defence_crush to it },
                row.magic?.let { params.defence_magic to it },
                row.light?.let { params.defence_light to it },
                row.standard?.let { params.defence_standard to it },
                row.heavy?.let { params.defence_heavy to it },
            )
            .toMap()
    }
}
