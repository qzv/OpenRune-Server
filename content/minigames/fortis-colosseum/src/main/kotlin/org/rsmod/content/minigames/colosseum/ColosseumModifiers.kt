package org.rsmod.content.minigames.colosseum

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.StructType
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.enums.ColosseumEnums
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.table.ColosseumModifierOffersRow
import org.rsmod.game.entity.Player

internal enum class Modifier(val stacksVarbit: String?) {
    Mantimayhem("varbit.colosseum_modifier_mantimayhem_stacks_client"),
    Reentry("varbit.colosseum_modifier_reentry_stacks_client"),
    Bees("varbit.colosseum_modifier_toxicity_stacks_client"),
    Volatility("varbit.colosseum_modifier_volatility_stacks_client"),
    Blasphemy("varbit.colosseum_modifier_blasphemy_stacks_client"),
    Relentless("varbit.colosseum_modifier_relentless_stacks_client"),
    Quartet(null),
    Totemic(null),
    Doom("varbit.colosseum_modifier_doom_stacks_client"),
    DynamicDuo(null),
    Solarflare("varbit.colosseum_modifier_solarflare_stacks_client"),
    Myopia("varbit.colosseum_modifier_myopia_stacks_client"),
    Frailty("varbit.colosseum_modifier_frailty_stacks_client"),
    RedFlag(null);

    val id: Int
        get() = ordinal

    companion object {
        fun of(id: Int): Modifier? = entries.getOrNull(id)
    }
}

@Singleton
internal class ColosseumModifiers @Inject constructor(private val random: GameRandom) {
    private val structs: Map<Modifier, StructType> by lazy {
        val enum = ColosseumEnums.colosseum_modifiers
        Modifier.entries.associateWith { modifier ->
            val structId = checkNotNull(enum.backing[modifier.id]) { "No struct for $modifier." }
            checkNotNull(ServerCacheManager.getStruct(structId)) { "Missing struct for $modifier." }
        }
    }

    private val priorities: Map<Int, Int> by lazy {
        ColosseumModifierOffersRow.all().associate { it.modifier to it.ownedPriority }
    }

    fun name(modifier: Modifier): String =
        structs.getValue(modifier).getString(NAME_PARAM.asRSCM(RSCMType.PARAM))

    fun glory(modifier: Modifier): Int =
        structs.getValue(modifier).getInt(GLORY_PARAM.asRSCM(RSCMType.PARAM))

    fun levelled(modifier: Modifier): Boolean =
        when (
            val value =
                structs.getValue(modifier).params?.get(LEVELLED_PARAM.asRSCM(RSCMType.PARAM))
        ) {
            is Boolean -> value
            is Number -> value.toInt() == 1
            else -> false
        }

    fun level(player: Player, modifier: Modifier): Int {
        if (!owns(player, modifier)) return 0
        val varbit = modifier.stacksVarbit ?: return 1
        return player.vars[varbit]
    }

    fun owns(player: Player, modifier: Modifier): Boolean =
        player.ownedModifiers and (1 shl modifier.id) != 0

    fun maxed(player: Player, modifier: Modifier): Boolean {
        if (!owns(player, modifier)) return false
        return !levelled(modifier) || level(player, modifier) >= MAX_LEVEL
    }

    fun ownedGlory(player: Player): Int = Modifier.entries.sumOf { level(player, it) * glory(it) }

    fun apply(player: Player, modifier: Modifier) {
        player.ownedModifiers = player.ownedModifiers or (1 shl modifier.id)
        modifier.stacksVarbit?.let {
            VarPlayerIntMapSetter.set(player, it, (player.vars[it] + 1).coerceAtMost(MAX_LEVEL))
        }
        player.lastModifierGlory = glory(modifier)
    }

    fun reset(player: Player) {
        player.ownedModifiers = 0
        for (modifier in Modifier.entries) {
            modifier.stacksVarbit?.let { VarPlayerIntMapSetter.set(player, it, 0) }
        }
        player.selectedModifier = 0
    }

    fun rollOffers(player: Player, nextWave: Int) {
        val rows = ColosseumModifierOffersRow.all()
        val picks =
            if (nextWave == 1) {
                rows
                    .filter { it.firstOfferSlot > 0 }
                    .sortedBy { it.firstOfferSlot }
                    .map { it.modifier }
            } else {
                drawOffers(player, nextWave, rows)
            }
        player.offerA = picks.getOrElse(0) { -1 }
        player.offerB = picks.getOrElse(1) { -1 }
        player.offerC = picks.getOrElse(2) { -1 }
        player.selectedModifier = 0
        player.offerWave = nextWave
    }

    private fun drawOffers(
        player: Player,
        nextWave: Int,
        rows: List<ColosseumModifierOffersRow>,
    ): List<Int> {
        val eligible =
            rows
                .filter { row -> Modifier.of(row.modifier)?.let { !maxed(player, it) } == true }
                .filter { nextWave >= it.minWave && (nextWave != SOL_WAVE || it.beforeSol) }
                .sortedBy { it.modifier }
                .toMutableList()
        val picks = mutableListOf<Int>()
        for (slot in 0 until OFFER_COUNT) {
            if (eligible.isEmpty()) break
            val owned =
                eligible
                    .filter { row -> Modifier.of(row.modifier)?.let { owns(player, it) } == true }
                    .sortedBy { row -> priorities.getValue(row.modifier) }
            val pick =
                owned.firstOrNull { random.of(PERMILLE) < OWNED_ACCEPT_PERMILLE[slot] }
                    ?: eligible[random.of(eligible.size)]
            eligible.remove(pick)
            picks += pick.modifier
        }
        return picks
    }

    fun offered(player: Player, slot: Int): Modifier? =
        when (slot) {
            1 -> Modifier.of(player.offerA)
            2 -> Modifier.of(player.offerB)
            3 -> Modifier.of(player.offerC)
            else -> null
        }

    private fun StructType.string(param: String): String =
        params?.get(param.asRSCM(RSCMType.PARAM)) as? String ?: ""

    private fun StructType.int(param: String): Int =
        (params?.get(param.asRSCM(RSCMType.PARAM)) as? Number)?.toInt() ?: 0

    private fun StructType.bool(param: String): Boolean =
        when (val value = params?.get(param.asRSCM(RSCMType.PARAM))) {
            is Boolean -> value
            is Number -> value.toInt() == 1
            else -> false
        }

    companion object {
        const val MAX_LEVEL = 3
        const val OFFER_COUNT = 3
        const val SOL_WAVE = 12
        private const val PERMILLE = 1000
        private val OWNED_ACCEPT_PERMILLE = intArrayOf(665, 400, 0)

        private const val NAME_PARAM = "param.colosseum_modifier_name"
        private const val GLORY_PARAM = "param.colosseum_modifier_glory"
        private const val LEVELLED_PARAM = "param.colosseum_modifier_levelled"
    }
}
