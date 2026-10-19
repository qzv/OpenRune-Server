package org.rsmod.content.bosses.yama.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object YamaContractsTable {
    const val CONTRACT = 0
    const val TITLE = 1
    const val MAX_HP = 2
    const val ACQUISITION = 3
    const val GLYPH_TICKS = 4
    const val PENETRATION = 5
    const val RETURNED = 6
    const val PARTNER_ALLOWED = 7
    const val REWARD = 8
    const val REWARD_COUNT = 9
    const val OVERLAY_MODEL = 10
    const val CONTINUOUS_FLARES = 11
    const val PRAYER_PIERCE = 12
    const val FOUR_FLARES = 13
    const val HAZARDS_AHEAD = 14
    const val DRAINS_RUN_ENERGY = 15
    const val FORCES_PRAYER_OFF = 16
    const val PURSUES = 17
    const val DOUBLES_SPECIAL_REGEN = 18
    const val GLYPH_COST_SCALE = 19
    const val HALVES_RESTORES = 20
    const val BLOCKS_PRAYER_RESTORE = 21
    const val BLOCKS_HEALING = 22
    const val LETHAL_FLARES = 23
    const val HALVES_ATTACK_RANGE = 24
    const val FLARES_ALWAYS_HIT = 25
    const val PIECE_CHOICE = 26
    const val KEEPS_FULL_TABLE = 27
    const val PET_RATE = 28

    private const val BASE_MAX_HP = 2500
    private const val GLYPHIC_MAX_HP = 2875
    private const val BLOODIED_MAX_HP = 3000
    private const val BASE_GLYPH_TICKS = 10
    private const val GLYPHIC_GLYPH_TICKS = 2
    private const val FULL_PENETRATION = 100
    private const val BLOODIED_PENETRATION = 5
    private const val GLYPHIC_GLYPH_COST_SCALE = 3
    private const val FAMILIAR_PET_RATE = 100
    private const val SENSORY_FORFEIT_OVERLAY = 56227
    private const val STANDARD_OVERLAY = 56228
    private const val FAMILIAR_BLOODIED_OVERLAY = 56230

    private class Contract(
        val key: String,
        val obj: String,
        val title: String,
        val maxHp: Int = BASE_MAX_HP,
        val acquisition: Boolean = false,
        val glyphTicks: Int = BASE_GLYPH_TICKS,
        val penetration: Int = 0,
        val returned: Boolean = false,
        val partnerAllowed: Boolean = false,
        val reward: String? = null,
        val rewardCount: Int = 1,
        val overlayModel: Int? = null,
        val continuousFlares: Boolean = false,
        val prayerPierce: Boolean = false,
        val fourFlares: Boolean = acquisition,
        val hazardsAhead: Boolean = false,
        val drainsRunEnergy: Boolean = false,
        val forcesPrayerOff: Boolean = false,
        val pursues: Boolean = false,
        val doublesSpecialRegen: Boolean = false,
        val glyphCostScale: Int = 1,
        val halvesRestores: Boolean = acquisition,
        val blocksPrayerRestore: Boolean = false,
        val blocksHealing: Boolean = false,
        val lethalFlares: Boolean = false,
        val halvesAttackRange: Boolean = false,
        val flaresAlwaysHit: Boolean = false,
        val pieceChoice: Boolean = false,
        val keepsFullTable: Boolean = false,
        val petRate: Int? = null,
    )

    private val CONTRACTS =
        listOf(
            Contract(
                "catalyst",
                "obj.yama_catalyst_contract",
                "Contract of catalyst acquisition",
                acquisition = true,
                partnerAllowed = true,
                reward = "obj.cosmic_soul_catalyst",
                rewardCount = 2000,
                overlayModel = STANDARD_OVERLAY,
            ),
            Contract(
                "worm",
                "obj.yama_worm_contract",
                "Contract of worm acquisition",
                acquisition = true,
                partnerAllowed = true,
                reward = "obj.diabolic_worms",
                rewardCount = 250,
            ),
            Contract(
                "shard",
                "obj.yama_shard_contract",
                "Contract of shard acquisition",
                acquisition = true,
                partnerAllowed = true,
                reward = "obj.oathplate_shards",
                rewardCount = 45,
                overlayModel = STANDARD_OVERLAY,
            ),
            Contract(
                "oathplate",
                "obj.yama_armour_contract",
                "Contract of oathplate acquisition",
                acquisition = true,
                overlayModel = STANDARD_OVERLAY,
                pieceChoice = true,
            ),
            Contract(
                "harmony",
                "obj.yama_horn_contract",
                "Contract of harmony acquisition",
                acquisition = true,
                reward = "obj.soulflame_horn",
            ),
            Contract(
                "familiar",
                "obj.yama_pet_contract",
                "Contract of familiar acquisition",
                returned = true,
                partnerAllowed = true,
                overlayModel = FAMILIAR_BLOODIED_OVERLAY,
                continuousFlares = true,
                prayerPierce = true,
                hazardsAhead = true,
                doublesSpecialRegen = true,
                blocksPrayerRestore = true,
                blocksHealing = true,
                keepsFullTable = true,
                petRate = FAMILIAR_PET_RATE,
            ),
            Contract(
                "forfeit",
                "obj.yama_binding_contract",
                "Contract of forfeit breath",
                returned = true,
                reward = "obj.purifying_sigil_top",
                overlayModel = SENSORY_FORFEIT_OVERLAY,
                hazardsAhead = true,
                drainsRunEnergy = true,
            ),
            Contract(
                "glyphic",
                "obj.yama_special_contract",
                "Contract of glyphic attenuation",
                maxHp = GLYPHIC_MAX_HP,
                glyphTicks = GLYPHIC_GLYPH_TICKS,
                returned = true,
                reward = "obj.purifying_sigil_bottom",
                overlayModel = STANDARD_OVERLAY,
                glyphCostScale = GLYPHIC_GLYPH_COST_SCALE,
            ),
            Contract(
                "sensory",
                "obj.yama_spell_contract",
                "Contract of sensory clouding",
                penetration = FULL_PENETRATION,
                returned = true,
                reward = "obj.purifying_sigil_left",
                overlayModel = SENSORY_FORFEIT_OVERLAY,
                pursues = true,
                lethalFlares = true,
                halvesAttackRange = true,
            ),
            Contract(
                "divine",
                "obj.yama_heavyranged_contract",
                "Contract of divine severance",
                returned = true,
                reward = "obj.purifying_sigil_right",
                overlayModel = STANDARD_OVERLAY,
                continuousFlares = true,
                forcesPrayerOff = true,
                lethalFlares = true,
            ),
            Contract(
                "bloodied",
                "obj.yama_2h_contract",
                "Contract of bloodied blows",
                maxHp = BLOODIED_MAX_HP,
                penetration = BLOODIED_PENETRATION,
                returned = true,
                reward = "obj.purifying_sigil_middle",
                overlayModel = FAMILIAR_BLOODIED_OVERLAY,
                fourFlares = true,
                flaresAlwaysHit = true,
            ),
        )

    fun contracts() =
        dbTable("dbtable.yama_contracts", serverOnly = true) {
            column("contract", CONTRACT, VarType.OBJ)
            column("title", TITLE, VarType.STRING)
            column("max_hp", MAX_HP, VarType.INT)
            column("acquisition", ACQUISITION, VarType.BOOLEAN)
            column("glyph_ticks", GLYPH_TICKS, VarType.INT)
            column("penetration", PENETRATION, VarType.INT)
            column("returned", RETURNED, VarType.BOOLEAN)
            column("partner_allowed", PARTNER_ALLOWED, VarType.BOOLEAN)
            column("reward", REWARD, VarType.OBJ)
            column("reward_count", REWARD_COUNT, VarType.INT)
            column("overlay_model", OVERLAY_MODEL, VarType.MODEL)
            column("continuous_flares", CONTINUOUS_FLARES, VarType.BOOLEAN)
            column("prayer_pierce", PRAYER_PIERCE, VarType.BOOLEAN)
            column("four_flares", FOUR_FLARES, VarType.BOOLEAN)
            column("hazards_ahead", HAZARDS_AHEAD, VarType.BOOLEAN)
            column("drains_run_energy", DRAINS_RUN_ENERGY, VarType.BOOLEAN)
            column("forces_prayer_off", FORCES_PRAYER_OFF, VarType.BOOLEAN)
            column("pursues", PURSUES, VarType.BOOLEAN)
            column("doubles_special_regen", DOUBLES_SPECIAL_REGEN, VarType.BOOLEAN)
            column("glyph_cost_scale", GLYPH_COST_SCALE, VarType.INT)
            column("halves_restores", HALVES_RESTORES, VarType.BOOLEAN)
            column("blocks_prayer_restore", BLOCKS_PRAYER_RESTORE, VarType.BOOLEAN)
            column("blocks_healing", BLOCKS_HEALING, VarType.BOOLEAN)
            column("lethal_flares", LETHAL_FLARES, VarType.BOOLEAN)
            column("halves_attack_range", HALVES_ATTACK_RANGE, VarType.BOOLEAN)
            column("flares_always_hit", FLARES_ALWAYS_HIT, VarType.BOOLEAN)
            column("piece_choice", PIECE_CHOICE, VarType.BOOLEAN)
            column("keeps_full_table", KEEPS_FULL_TABLE, VarType.BOOLEAN)
            column("pet_rate", PET_RATE, VarType.INT)
            for (contract in CONTRACTS) {
                row("dbrow.yama_contract_${contract.key}") {
                    columnRSCM(CONTRACT, contract.obj)
                    column(TITLE, contract.title)
                    column(MAX_HP, contract.maxHp)
                    column(ACQUISITION, contract.acquisition)
                    column(GLYPH_TICKS, contract.glyphTicks)
                    column(PENETRATION, contract.penetration)
                    column(RETURNED, contract.returned)
                    column(PARTNER_ALLOWED, contract.partnerAllowed)
                    contract.reward?.let {
                        columnRSCM(REWARD, it)
                        column(REWARD_COUNT, contract.rewardCount)
                    }
                    contract.overlayModel?.let { column(OVERLAY_MODEL, it) }
                    column(CONTINUOUS_FLARES, contract.continuousFlares)
                    column(PRAYER_PIERCE, contract.prayerPierce)
                    column(FOUR_FLARES, contract.fourFlares)
                    column(HAZARDS_AHEAD, contract.hazardsAhead)
                    column(DRAINS_RUN_ENERGY, contract.drainsRunEnergy)
                    column(FORCES_PRAYER_OFF, contract.forcesPrayerOff)
                    column(PURSUES, contract.pursues)
                    column(DOUBLES_SPECIAL_REGEN, contract.doublesSpecialRegen)
                    column(GLYPH_COST_SCALE, contract.glyphCostScale)
                    column(HALVES_RESTORES, contract.halvesRestores)
                    column(BLOCKS_PRAYER_RESTORE, contract.blocksPrayerRestore)
                    column(BLOCKS_HEALING, contract.blocksHealing)
                    column(LETHAL_FLARES, contract.lethalFlares)
                    column(HALVES_ATTACK_RANGE, contract.halvesAttackRange)
                    column(FLARES_ALWAYS_HIT, contract.flaresAlwaysHit)
                    column(PIECE_CHOICE, contract.pieceChoice)
                    column(KEEPS_FULL_TABLE, contract.keepsFullTable)
                    contract.petRate?.let { column(PET_RATE, it) }
                }
            }
        }
}
