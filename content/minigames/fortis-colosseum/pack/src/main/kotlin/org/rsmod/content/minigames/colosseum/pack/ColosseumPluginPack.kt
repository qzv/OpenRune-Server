package org.rsmod.content.minigames.colosseum.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.pack.PluginPack

class ColosseumPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> =
        listOf(
            ColosseumTables.waves(),
            ColosseumTables.spawns(),
            ColosseumWarbandSlots.slots(),
            ColosseumLootTables.tiers(),
            ColosseumLootTables.uniques(),
            ColosseumOfferTable.offers(),
            ColosseumEnemyTable.enemies(),
        )
}
