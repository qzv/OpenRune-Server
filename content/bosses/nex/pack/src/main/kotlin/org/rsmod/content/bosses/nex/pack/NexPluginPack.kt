package org.rsmod.content.bosses.nex.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.pack.PluginPack

class NexPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> = listOf(NexPhasesTable.phases())
}
