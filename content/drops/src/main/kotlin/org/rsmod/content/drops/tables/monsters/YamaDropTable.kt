package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.dropRollable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.game.entity.Player

private val FOODS = listOf("obj.pineapple_pizza", "obj.wild_pie")
private val POTIONS = listOf("obj.3doseprayerrestore", "obj.brutal_2dose2restore")
private val COMBAT_POTIONS = listOf("obj.1dose2combat", "obj.brutal_2dosepotionofzamorak")

@field:RegisterDropTable
@JvmField
public val yamaDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Yama Drops",
    npcs = npcs("npc.yama"),
    mainTable = rsPlayerWeightedTable(total = 648000) {
        name("Yama Drops")
        1080 weight "obj.oathplate_helm" count 1
        1080 weight "obj.oathplate_chest" count 1
        1080 weight "obj.oathplate_legs" count 1
        2160 weight "obj.soulflame_horn" count 1
        53550 weight "obj.yama_dossier" count 1
        19635 weight "obj.forgotten_lockbox" count 1
        37961 weight "obj.oathplate_shards" count 12
        531454 weight rsPlayerWeightedTable(total = 624) {
            for (food in FOODS) for (potion in POTIONS) for (combat in COMBAT_POTIONS) {
                15 weight dropRollable(
                    DropRollItem(
                        food,
                        3..4,
                        bonusDrops = listOf(DropRollItem(potion, 2), DropRollItem(combat, 1)),
                    )
                )
            }
            40 weight "obj.cert_rune_chainbody" count 8
            32 weight "obj.cert_battlestaff" count 40
            24 weight "obj.cert_rune_platebody" count 8
            16 weight "obj.dragon_plateskirt" count 1
            16 weight "obj.dragon_platelegs" count 1
            24 weight "obj.bloodrune" count 400
            24 weight "obj.lawrune" count 150
            16 weight "obj.smokerune" count 350
            16 weight "obj.soulrune" count 500
            16 weight "obj.soulrune" count 1000
            8 weight "obj.firerune" count 40000
            8 weight "obj.wrathrune" count 800
            56 weight "obj.cosmic_soul_catalyst" count 850
            56 weight "obj.diabolic_worms" count 90
            40 weight "obj.demonic_tallow_barrel_full" count 1
            32 weight "obj.teleportscroll_chasmoffire" count 6
            24 weight "obj.cert_emerald" count 40
            24 weight "obj.cert_ruby" count 40
            24 weight "obj.cert_diamond" count 40
            8 weight "obj.xbows_bolt_tips_onyx" count 150
        }
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 2500 weight "obj.yamapet" count 1
        1 outOf 30 weight "obj.trail_elite_emote_exp1" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_elite_emote_exp1")
        }
    },
)
