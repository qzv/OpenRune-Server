package org.rsmod.content.bosses.yama

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.table.YamaContractsRow

internal class ContractReward(val item: String, val count: Int)

internal enum class YamaContract(private val dbrow: String) {
    CATALYST("dbrow.yama_contract_catalyst"),
    WORM("dbrow.yama_contract_worm"),
    SHARD("dbrow.yama_contract_shard"),
    OATHPLATE("dbrow.yama_contract_oathplate"),
    HARMONY("dbrow.yama_contract_harmony"),
    FAMILIAR("dbrow.yama_contract_familiar"),
    FORFEIT("dbrow.yama_contract_forfeit"),
    GLYPHIC("dbrow.yama_contract_glyphic"),
    SENSORY("dbrow.yama_contract_sensory"),
    DIVINE("dbrow.yama_contract_divine"),
    BLOODIED("dbrow.yama_contract_bloodied");

    private val row: YamaContractsRow by lazy { YamaContractsRow.getRow(dbrow) }

    val item: String
        get() = RSCM.getReverseMapping(RSCMType.OBJ, row.contract.id)

    val title: String
        get() = row.title

    val maxHp: Int
        get() = row.maxHp

    val acquisition: Boolean
        get() = row.acquisition

    val glyphTicks: Int
        get() = row.glyphTicks

    val penetration: Int
        get() = row.penetration

    val returned: Boolean
        get() = row.returned

    val partnerAllowed: Boolean
        get() = row.partnerAllowed

    val rewards: List<ContractReward>
        get() {
            val reward = row.reward ?: return emptyList()
            val item = RSCM.getReverseMapping(RSCMType.OBJ, reward.id)
            return listOf(ContractReward(item, row.rewardCount ?: 1))
        }

    val overlayModel: Int?
        get() = row.overlayModel

    val continuousFlares: Boolean
        get() = row.continuousFlares

    val prayerPierce: Boolean
        get() = row.prayerPierce

    val fourFlares: Boolean
        get() = row.fourFlares

    val hazardsAhead: Boolean
        get() = row.hazardsAhead

    val drainsRunEnergy: Boolean
        get() = row.drainsRunEnergy

    val forcesPrayerOff: Boolean
        get() = row.forcesPrayerOff

    val pursues: Boolean
        get() = row.pursues

    val doublesSpecialRegen: Boolean
        get() = row.doublesSpecialRegen

    val glyphCostScale: Int
        get() = row.glyphCostScale

    val halvesRestores: Boolean
        get() = row.halvesRestores

    val blocksPrayerRestore: Boolean
        get() = row.blocksPrayerRestore

    val blocksHealing: Boolean
        get() = row.blocksHealing

    val lethalFlares: Boolean
        get() = row.lethalFlares

    val halvesAttackRange: Boolean
        get() = row.halvesAttackRange

    val flaresAlwaysHit: Boolean
        get() = row.flaresAlwaysHit

    val pieceChoice: Boolean
        get() = row.pieceChoice

    val keepsFullTable: Boolean
        get() = row.keepsFullTable

    val petRate: Int?
        get() = row.petRate
}

internal object YamaContracts {
    const val ATTACK_INTERVAL = 7
    const val FIRST_THRESHOLD = 0.75
    const val SECOND_THRESHOLD = 0.5
}

internal object YamaRunDrain {
    const val INTERVAL = 4
    const val UNIT = 100

    fun amount(ticksIntoFight: Int, damageTaken: Int): Int {
        val passive = if (ticksIntoFight % INTERVAL == 0) UNIT else 0
        return passive + maxOf(0, damageTaken) * UNIT
    }
}
