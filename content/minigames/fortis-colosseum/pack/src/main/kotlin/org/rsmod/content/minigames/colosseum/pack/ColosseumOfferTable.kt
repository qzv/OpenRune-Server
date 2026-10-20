package org.rsmod.content.minigames.colosseum.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object ColosseumOfferTable {
    const val MODIFIER = 0
    const val MIN_WAVE = 1
    const val BEFORE_SOL = 2
    const val FIRST_OFFER_SLOT = 3
    const val OWNED_PRIORITY = 4

    private const val DEFAULT_MIN_WAVE = 2
    private const val LATE_MODIFIER_MIN_WAVE = 7

    private class Offer(
        val id: Int,
        val minWave: Int = DEFAULT_MIN_WAVE,
        val beforeSol: Boolean = false,
        val firstSlot: Int = 0,
        val priority: Int = id,
    )

    private val OFFERS =
        listOf(
            Offer(0, priority = 4),
            Offer(1, priority = 0),
            Offer(2, beforeSol = true, priority = 1),
            Offer(3, beforeSol = true, priority = 2),
            Offer(4, beforeSol = true, firstSlot = 1, priority = 3),
            Offer(5, beforeSol = true, firstSlot = 2),
            Offer(6, beforeSol = true),
            Offer(7, beforeSol = true),
            Offer(8, beforeSol = true),
            Offer(9, minWave = LATE_MODIFIER_MIN_WAVE),
            Offer(10, beforeSol = true),
            Offer(11),
            Offer(12, beforeSol = true, firstSlot = 3),
            Offer(13, minWave = LATE_MODIFIER_MIN_WAVE),
        )

    fun offers() =
        dbTable("dbtable.colosseum_modifier_offers", serverOnly = true) {
            column("modifier", MODIFIER, VarType.INT)
            column("min_wave", MIN_WAVE, VarType.INT)
            column("before_sol", BEFORE_SOL, VarType.BOOLEAN)
            column("first_offer_slot", FIRST_OFFER_SLOT, VarType.INT)
            column("owned_priority", OWNED_PRIORITY, VarType.INT)
            for (offer in OFFERS) {
                row("dbrow.colosseum_offer_${offer.id}") {
                    column(MODIFIER, offer.id)
                    column(MIN_WAVE, offer.minWave)
                    column(BEFORE_SOL, offer.beforeSol)
                    column(FIRST_OFFER_SLOT, offer.firstSlot)
                    column(OWNED_PRIORITY, offer.priority)
                }
            }
        }
}
