package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.bits
import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcDeathDropContext
import org.rsmod.api.death.NpcDeathDropHook
import org.rsmod.api.death.NpcDropReceiveHook
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.npc.hit.NpcDamageContributor
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.table.PetDropsRow
import org.rsmod.content.drops.tables.monsters.nexTertiaryTable
import org.rsmod.content.drops.tables.monsters.nexUniqueTable
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.content.other.pets.PetRewards
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid
import org.rsmod.utils.bits.bitMask

internal data class NexDrop(val obj: String, val count: Int)

internal class NexLootRoll(
    val mvp: Boolean,
    commons: List<NexDrop>,
    rares: List<NexDrop>,
    pet: Boolean,
) {
    private var commons: List<NexDrop>? = commons
    private var rares: List<NexDrop>? = rares
    private var pet: Boolean = pet

    fun takeCommons(): List<NexDrop> {
        val taken = commons ?: return emptyList()
        commons = null
        return taken
    }

    fun takeRares(): List<NexDrop> {
        val taken = rares ?: return emptyList()
        rares = null
        return taken
    }

    fun takePet(): Boolean {
        val taken = pet
        pet = false
        return taken
    }
}

@Singleton
internal class NexLoot
@Inject
constructor(
    private val random: GameRandom,
    private val registry: DropTableRegistry,
    private val objRepo: ObjRepository,
    private val pets: PetRewards,
    private val playerList: PlayerList,
    private val dropHooks: Set<NpcDeathDropHook>,
    private val receiveHooks: Set<NpcDropReceiveHook>,
) {
    private var lootTrackerEvent = 0

    fun awards(fight: NexFight, nex: Npc, players: List<Player>): Map<Player, NexAward> {
        val damage = players.associateWith { fight.contributions.damageBy(it) }
        val nexDamage = players.associateWith { nex.damageContributions.damageBy(it) }
        return NexLootRules.awards(damage, nexDamage)
    }

    fun roll(player: Player, award: NexAward): NexLootRoll {
        val commons = ArrayList<NexDrop>()
        if (award.mvp) commons += NexDrop(BIG_BONES, 1)
        for (drop in rolled(registry.forNpc(NEX_TABLE)?.roll(player, ArgMap()), player)) {
            val count = NexLootRules.quantity(drop.count, award, stackable(drop.obj))
            commons += NexDrop(drop.obj, count)
        }
        val rares = ArrayList<NexDrop>()
        if (NexLootRules.hits(award, UNIQUE_RATE, random.of(UNIQUE_RATE * NexLootRules.SCALE))) {
            rares += rolled(nexUniqueTable.roll(player, ArgMap()), player)
        }
        rares += rolled(nexTertiaryTable.roll(player, ArgMap()), player)
        val rate = PET_RATE
        val pet = rate > 0 && NexLootRules.hits(award, rate, random.of(rate * NexLootRules.SCALE))
        return NexLootRoll(award.mvp, commons, rares, pet)
    }

    fun nextEvent(): Int {
        lootTrackerEvent = if (lootTrackerEvent == Int.MAX_VALUE) 1 else lootTrackerEvent + 1
        return lootTrackerEvent
    }

    fun payCommons(player: Player, roll: NexLootRoll, coords: CoordGrid, event: Int) {
        val drops = roll.takeCommons()
        val announced = if (roll.mvp) drops.drop(1) else drops
        if (roll.mvp) drops.firstOrNull()?.let { give(player, it, coords, event) }
        if (announced.isNotEmpty()) {
            player.mes("<col=ef0083>Nex dropped: ${announced.joinToString { label(it) }}</col>")
        }
        announced.forEach { give(player, it, coords, event) }
    }

    fun payRares(player: Player, roll: NexLootRoll, coords: CoordGrid, event: Int) {
        roll.takeRares().forEach { give(player, it, coords, event) }
        if (roll.takePet() && pets.give(player, PET)) CollectionLog.grant(player, PET, 1)
    }

    private fun addEssence(player: Player, amount: Int) {
        val varbit = ESSENCE_VARBIT
        val type = ServerCacheManager.getVarbit(varbit.asRSCM(RSCMType.VARBIT))
        val max = type?.bits?.bitMask ?: Int.MAX_VALUE.toLong()
        val next = (player.vars[varbit].toLong() + amount).coerceAtMost(max).toInt()
        VarPlayerIntMapSetter.set(player, varbit, next)
    }

    fun nexEssence(player: Player) {
        addEssence(player, NEX_ESSENCE)
    }

    fun mageEssence(mage: Npc) {
        mage.damageContributions.topPlayer(playerList)?.let { addEssence(it, MAGE_ESSENCE) }
    }

    private fun give(player: Player, drop: NexDrop, coords: CoordGrid, event: Int) {
        if (drop.count <= 0) return
        CollectionLog.grant(player, drop.obj, drop.count)
        if (receiveHooks.any { it.tryReceive(player, drop.obj, drop.count) }) return
        val duration = player.lootDropDuration ?: constants.lootdrop_duration
        if (drop.count == 1 && consumed(player, drop.obj, coords, duration)) return
        val spawned = objRepo.add(drop.obj, coords, duration, player, drop.count)
        val npcId = DYING.asRSCM(RSCMType.NPC)
        ClientScripts.lootTrackerAddLoot(player, npcId, event, spawned.type, spawned.count)
    }

    private fun consumed(player: Player, obj: String, coords: CoordGrid, duration: Int): Boolean {
        val type = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)) ?: return false
        val context = NpcDeathDropContext(player, type, coords, duration, objRepo)
        return dropHooks.any { it.tryConsume(context) }
    }

    private fun rolled(result: RollResult<DropRollItem>?, player: Player): List<NexDrop> {
        val items =
            when (val flat = result?.flatten()) {
                is RollResult.Single -> listOf(flat.result)
                is RollResult.ListOf -> flat.results
                else -> emptyList()
            }
        val drops = ArrayList<NexDrop>()
        for (item in items) expand(item, player, drops)
        return drops
    }

    private fun expand(item: DropRollItem, player: Player, into: MutableList<NexDrop>) {
        if (item.isNothing || !item.condition(player)) return
        into += NexDrop(item.transformObj(player) ?: item.obj, item.rollCount(random))
        item.bonusDrops.forEach { expand(it, player, into) }
    }

    private fun stackable(obj: String): Boolean =
        ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.isStackable ?: true

    private fun label(drop: NexDrop): String {
        val type = ServerCacheManager.getItem(drop.obj.asRSCM(RSCMType.OBJ))
        val cert = type?.takeIf { it.isCert }
        val named = cert?.let { ServerCacheManager.getItem(it.certlink) } ?: type
        val name = named?.name ?: drop.obj
        return if (drop.count > 1) "${"%,d".format(drop.count)} x $name" else name
    }

    private companion object {
        private const val NEX_TABLE = "npc.nex"
        private const val DYING = "npc.nex_dying"
        private const val BIG_BONES = "obj.big_bones"
        private const val PET = "obj.nexpet"
        private const val UNIQUE_RATE = NexLootRules.UNIQUE_RATE
        private const val ESSENCE_VARBIT = "varbit.godwars_counter_zaros"
        private const val NEX_ESSENCE = 5
        private const val MAGE_ESSENCE = 2
        private val PET_RATE: Int by lazy {
            PetDropsRow.all().firstOrNull { it.pet.id == PET.asRSCM(RSCMType.OBJ) }?.rate ?: 0
        }
    }
}

internal class NexContributionHook @Inject constructor(private val fights: NexFights) :
    NpcDamageContributor {
    override fun onPlayerDamageNpc(npc: Npc, source: Player, damage: Int) {
        if (damage <= 0) return
        fights.fightForTarget(npc)?.contributions?.record(source, damage)
    }
}
