package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.RSPreRollTable
import dtx.rs.RSWeightedTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.dropRollable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.game.entity.Player

private const val NEX_ELITE_CLUE = "obj.trail_elite_emote_exp1"

@field:RegisterDropTable
@JvmField
public val nexDropTable: RSDropTable<Player, DropRollItem> =
    RSDropTable(
        tableIdentifier = "Nex Drops",
        npcs = npcs("npc.nex", "npc.nex_soulsplit", "npc.nex_deflect", "npc.nex_dying"),
        mainTable =
            rsPlayerWeightedTable(total = 17103) {
                name("Nex Drops")
                2484 weight
                    dropRollable(
                        DropRollItem(
                            "obj.4dosepotionofsaradomin",
                            10,
                            bonusDrops = listOf(DropRollItem("obj.4dose2restore", 5)),
                        )
                    )
                1242 weight
                    dropRollable(
                        DropRollItem(
                            "obj.shark",
                            10,
                            bonusDrops = listOf(DropRollItem("obj.4doseprayerrestore", 3)),
                        )
                    )
                207 weight "obj.airrune" count 10000
                207 weight "obj.firerune" count 10000
                414 weight "obj.waterrune" count 10000
                1449 weight "obj.bloodrune" count 84..876
                1035 weight "obj.deathrune" count 85..986
                1449 weight "obj.soulrune" count 86..592
                828 weight "obj.dragon_bolts_unfeathered" count 12..283
                207 weight "obj.xbows_crossbow_bolts_runite_tipped_onyx_enchanted" count 11..29
                1242 weight "obj.mcannonball" count 42..2252
                414 weight "obj.cert_air_orb" count 6..100
                207 weight "obj.cert_coal" count 23..2354
                207 weight "obj.cert_runite_ore" count 2..30
                1242 weight "obj.cert_uncut_ruby" count 3..80
                1035 weight "obj.cert_uncut_diamond" count 3..64
                621 weight "obj.cert_wine_of_zamorak" count 4..64
                207 weight "obj.coins" count 8539..26748
                414 weight "obj.ecumenical_key_shard" count 6..39
                209 weight "obj.blood_essence_inactive" count 1..2
                82 weight "obj.rune_sword" count 1
                1045 weight "obj.nihil_shard" count 80..85
                656 weight "obj.nihil_shard" count 85..95
            },
        mainRolls = 2,
    )

@JvmField
public val nexUniqueTable: RSWeightedTable<Player, DropRollItem> =
    rsPlayerWeightedTable(total = 12) {
        name("Nex Uniques")
        2 weight "obj.broken_torva_helm" count 1
        2 weight "obj.broken_torva_chest" count 1
        2 weight "obj.broken_torva_legs" count 1
        2 weight "obj.nihil_horn" count 1
        3 weight "obj.zaryte_vambraces" count 1
        1 weight "obj.godwars_godsword_hilt_ancient" count 1
    }

@JvmField
public val nexTertiaryTable: RSPreRollTable<Player, DropRollItem> =
    rsPlayerTertiaryTable {
        1 outOf 48 weight NEX_ELITE_CLUE count 1 transformObj { player ->
            player.clueScrollTransformObj(NEX_ELITE_CLUE)
        }
    }
