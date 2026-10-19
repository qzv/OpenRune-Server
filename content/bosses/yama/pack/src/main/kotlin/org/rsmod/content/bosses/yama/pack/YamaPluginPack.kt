package org.rsmod.content.bosses.yama.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.pack.PluginPack

class YamaPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> =
        listOf(
            YamaContractsTable.contracts(),
            YamaContractDropsTable.drops(),
            YamaArenaTable.arena(),
            YamaGlyphsTable.slots(),
            YamaGlyphsTable.costs(),
            YamaElementsTable.elements(),
            YamaDefencesTable.defences(),
            YamaFinalCycleTable.cycle(),
            YamaLinesTable.lines(),
            YamaFlareTilesTable.tiles(),
        )
}
