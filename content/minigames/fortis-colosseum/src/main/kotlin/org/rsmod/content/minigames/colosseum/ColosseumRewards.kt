package org.rsmod.content.minigames.colosseum

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invClear
import org.rsmod.api.invtx.invTransfer
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.random.GameRandom
import org.rsmod.api.table.ColosseumLootTiersRow
import org.rsmod.api.table.ColosseumUniqueTablesRow
import org.rsmod.api.table.ColosseumWavesRow
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.content.interfaces.collectionlog.collectionTransmit
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.uncert

@Singleton
class ColosseumRewards
@Inject
constructor(
    private val random: GameRandom,
    private val prices: MarketPrices,
    private val worldQueues: WorldQueueList,
) {
    fun earned(player: Player): Inventory = player.invMap.getOrPut(REWARDS_INV)

    fun future(player: Player): Inventory = player.invMap.getOrPut(FUTURE_INV)

    fun previous(player: Player): Inventory = player.invMap.getOrPut(PREVIOUS_INV)

    fun reset(player: Player) {
        player.invClear(earned(player))
        player.invClear(future(player))
        player.invClear(previous(player))
        player.lootOpened = false
    }

    fun preRoll(player: Player, wave: Int) {
        player.invClear(future(player))
        for ((obj, count) in roll(player, wave)) player.invAdd(
            future(player),
            obj,
            count,
            strict = false,
        )
    }

    fun bankWave(player: Player) {
        player.invClear(previous(player))
        for (obj in future(player).objs.filterNotNull()) {
            player.invAdd(earned(player), obj.id, obj.count, strict = false)
            player.invAdd(previous(player), obj.id, obj.count, strict = false)
        }
        player.invClear(future(player))
    }

    fun value(inv: Inventory): Long =
        inv.objs.filterNotNull().sumOf { obj ->
            val type = ServerCacheManager.getItem(obj.id) ?: return@sumOf 0L
            (prices[uncert(type)] ?: 0).toLong() * obj.count
        }

    fun roll(player: Player, wave: Int): List<Pair<Int, Int>> {
        val row = ColosseumWavesRow.all().first { it.wave == wave }
        val drops = mutableListOf<Pair<Int, Int>>()
        row.guaranteed?.let { drops += it.id to 1 }
        val unique = row.uniqueTable
        if (unique != null && row.uniqueChance > 0 && random.of(row.uniqueChance) == 0) {
            drops += rollUnique(player, unique)
        } else {
            val rare = row.rareTier
            val rareChance = row.rareTierChance
            val rolledRare = rare != null && rareChance != null && random.of(rareChance) == 0
            drops += rollTier(if (rolledRare) checkNotNull(rare) else row.mainTier)
        }
        return drops
    }

    private fun rollTier(tier: Int): Pair<Int, Int> {
        val row = ColosseumLootTiersRow.all().first { it.tier == tier }
        val index = random.of(row.objs.size)
        return row.objs[index].id to row.counts[index]
    }

    private fun rollUnique(player: Player, table: ColosseumUniqueTablesRow): Pair<Int, Int> {
        val total = table.weights.sum()
        var roll = random.of(total)
        var picked = table.objs.last().id
        for ((index, weight) in table.weights.withIndex()) {
            if (roll < weight) {
                picked = table.objs[index].id
                break
            }
            roll -= weight
        }
        val dedupe = table.dedupeObjs.map { it.id }
        if (picked in dedupe) picked = missingPiece(player, dedupe) ?: picked
        if (picked == table.bonusObj.id) return picked to bonusCount(table)
        return picked to 1
    }

    private fun bonusCount(table: ColosseumUniqueTablesRow): Int {
        if (random.of(table.bonusChance) != 0) return 1
        return table.bonusMin + random.of(table.bonusMax - table.bonusMin + 1)
    }

    private fun missingPiece(player: Player, pieces: List<Int>): Int? {
        val missing = pieces.filter { player.collectionTransmit.objs.none { obj -> obj?.id == it } }
        if (missing.isEmpty()) return null
        return missing[random.of(missing.size)]
    }

    fun ProtectedAccess.openChest() {
        if (earned(player).objs.all { it == null }) {
            mes(EMPTY_CHEST)
            return
        }
        val firstOpen = !player.lootOpened
        player.lootOpened = true
        if (firstOpen) announceLoot(player)
        invTransmit(earned(player))
        ifOpenMainModal(CHEST_INTERFACE)
        runClientScript(
            CHEST_INIT_SCRIPT.asRSCM(RSCMType.CLIENTSCRIPT),
            player.runWave,
            player.ownedModifiers,
        )
        ifSetEvents("$CHEST:tab_content_1_mod_list_content", 0..26, IfEvent.Op1)
        ifSetEvents(
            "$CHEST:tab_content_2_content",
            0..30,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op5,
        )
        interfaceInvInit(
            earned(player),
            LEGACY_ITEMS,
            objRowCount = CHEST_ROWS,
            objColCount = CHEST_COLUMNS,
            op1 = "Take",
            op2 = "Take-5",
            op3 = "Take-All",
            op4 = "Take-X",
            op5 = "",
        )
        refreshChestValue()
    }

    private fun announceLoot(player: Player) {
        val drops = earned(player).objs.filterNotNull().map { it.id to it.count }
        worldQueues.add(1) {
            for ((id, count) in drops) {
                val type = ServerCacheManager.getItem(id) ?: continue
                val label = if (count > 1) "$count x ${type.name}" else type.name
                val value = (prices[uncert(type)] ?: 0).toLong() * count
                when {
                    !type.tradeable -> player.mes("<col=ef1020>Untradeable drop: $label</col>")
                    value >= VALUABLE_DROP_VALUE ->
                        player.mes(
                            "<col=ef1020>Valuable drop: $label (${"%,d".format(value)} coins)</col>"
                        )
                }
                CollectionLog.grant(player, id, count)
            }
        }
    }

    fun ProtectedAccess.refreshChestValue() {
        val total = value(earned(player))
        runClientScript(CHEST_VALUE_SCRIPT.asRSCM(RSCMType.CLIENTSCRIPT), total)
        ifSetText(LEGACY_VALUE_TEXT, "Total Value: ${"%,d".format(total)}")
    }

    fun ProtectedAccess.take(comsub: Int, count: Int) {
        if (count <= 0) return
        val slot = slotOfDrawnItem(earned(player), comsub) ?: return
        if (move(slot, count, inv) == 0) mes(NO_SPACE_ANY)
        finishTake()
    }

    suspend fun ProtectedAccess.takeAll(into: Inventory) {
        val chest = earned(player)
        val toBank = into != inv
        if (chest.objs.any { it != null }) {
            val moved =
                chest.objs.indices.sumOf { slot ->
                    chest[slot]?.let { move(slot, it.count, into) } ?: 0
                }
            if (moved == 0) mes(if (toBank) BANK_FULL else NO_SPACE_ALL)
        } else if (toBank) {
            mes(NOTHING_TO_DEPOSIT)
        }
        finishTake()
        if (chest.objs.all { it == null }) {
            delay(1)
            ifClose()
        }
    }

    suspend fun ProtectedAccess.discard() {
        invStopTransmit(earned(player))
        ifClose()
        val confirmed =
            choice2(
                "Yes, discard my rewards.",
                true,
                "No, keep my rewards.",
                false,
                title = DISCARD_PROMPT,
            )
        if (!confirmed) {
            openChest()
            return
        }
        player.invClear(earned(player))
        mes(DISCARD_MESSAGE)
        refreshChestValue()
    }

    private fun ProtectedAccess.move(slot: Int, count: Int, into: Inventory): Int {
        val chest = earned(player)
        val obj = chest[slot] ?: return 0
        val toBank = into != inv
        val type = ServerCacheManager.getItem(obj.id)
        val noted =
            !toBank && !isUnique(obj.id) && type != null && !type.stackable && type.certlink > 0
        player.invTransfer(
            chest,
            slot,
            minOf(count, obj.count),
            into,
            strict = false,
            cert = noted,
            uncert = toBank,
        )
        return obj.count - (chest[slot]?.count ?: 0)
    }

    private fun ProtectedAccess.finishTake() {
        val chest = earned(player)
        val remaining = chest.objs.filterNotNull().map { it.id to it.count }
        player.invClear(chest)
        for ((id, count) in remaining) player.invAdd(chest, id, count, strict = false)
        refreshChestValue()
    }

    private fun isUnique(objId: Int): Boolean =
        ColosseumWavesRow.all().any { it.guaranteed?.id == objId } ||
            ColosseumUniqueTablesRow.all().any { row -> row.objs.any { it.id == objId } }

    fun setLegacyValue(player: Player, value: Long) {
        player.ifSetText(LEGACY_VALUE_TEXT, "Total Value: ${"%,d".format(value)}")
    }

    fun stopTransmit(player: Player) {
        player.stopInvTransmit(earned(player))
        player.stopInvTransmit(future(player))
        player.stopInvTransmit(previous(player))
    }

    fun ProtectedAccess.examine(comsub: Int) {
        val slot = slotOfDrawnItem(earned(player), comsub) ?: return
        objExamine(earned(player), slot)
    }

    private fun slotOfDrawnItem(inv: Inventory, comsub: Int): Int? =
        inv.objs.withIndex().filter { it.value != null }.getOrNull(comsub)?.index

    companion object {
        const val REWARDS_INV = "inv.colosseum_rewards"
        const val FUTURE_INV = "inv.colosseum_rewards_future"
        const val PREVIOUS_INV = "inv.colosseum_rewards_previous"

        const val CHEST_INTERFACE = "interface.colosseum_reward_chest_2"
        const val CHEST = "component.colosseum_reward_chest_2"
        private const val VALUABLE_DROP_VALUE = 30_000L
        private const val LEGACY_VALUE_TEXT =
            "component.colosseum_reward_chest:colosseum_reward_value"
        private const val CHEST_INIT_SCRIPT =
            "clientscript.[clientscript,colosseum_reward_chest_2_init]"
        private const val LEGACY_ITEMS = "component.colosseum_reward_chest:items"
        private const val CHEST_ROWS = 4
        private const val CHEST_COLUMNS = 4
        private const val CHEST_VALUE_SCRIPT = "clientscript.[clientscript,script4929]"

        private const val EMPTY_CHEST = "The chest appears to be empty."
        private const val NO_SPACE_ANY = "You don't have enough inventory space to take any."
        private const val NO_SPACE_ALL = "You don't have enough space to take anything."
        private const val BANK_FULL = "Your bank cannot hold your items."
        private const val NOTHING_TO_DEPOSIT = "You have nothing to deposit."
        private const val DISCARD_PROMPT =
            "Are you sure? You can't get your discarded rewards back!"
        private const val DISCARD_MESSAGE = "The chest empties before your eyes..."
    }
}
