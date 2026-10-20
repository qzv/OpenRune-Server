package org.rsmod.content.minigames.colosseum.enemies

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.dsl.MeleeAttackType
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.suppressAttacks
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.table.ColosseumEnemiesRow
import org.rsmod.content.minigames.colosseum.ColosseumModifiers
import org.rsmod.content.minigames.colosseum.ColosseumNpcs
import org.rsmod.content.minigames.colosseum.ColosseumSand
import org.rsmod.content.minigames.colosseum.ColosseumWaves
import org.rsmod.content.minigames.colosseum.Modifier
import org.rsmod.content.minigames.colosseum.WaveState
import org.rsmod.content.minigames.colosseum.enemies.ColosseumEnemyVarns.setTickVarn
import org.rsmod.content.minigames.colosseum.enemies.ColosseumEnemyVarns.tickVarn
import org.rsmod.content.minigames.colosseum.modifiers.PoolRules
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal object ColosseumEnemyHandlers {
    const val ARTILLERY_MARK = "colosseum.artillery_mark"
    const val ARTILLERY_LAND = "colosseum.artillery_land"
    const val MINOTAUR_HEAL = "colosseum.minotaur_heal"
    const val MINOTAUR_CRUSH = "colosseum.minotaur_crush"
    const val JAGUAR_CLAWS = "colosseum.jaguar_claws"
    const val PICK_ORDER = "colosseum.manticore_pick_order"
    const val CLEAR_ORB = "colosseum.manticore_clear_orb"
    const val MARK_THROW = "colosseum.manticore_mark_throw"
    const val VENOM = "colosseum.venom"
}

@Singleton
internal class ManticoreThrows
@Inject
constructor(private val deps: BossDeps, private val waves: ColosseumWaves) {
    fun mark(npc: Npc) {
        npc.setTickVarn(ColosseumEnemyVarns.LAST_THROW, deps.mapClock.cycle)
    }

    fun blocked(npc: Npc): Boolean {
        val state = waves.stateOf(npc) ?: return false
        val now = deps.mapClock.cycle
        return state.enemies.any { other ->
            val thrown = other.tickVarn(ColosseumEnemyVarns.LAST_THROW)
            other !== npc && thrown != null && now - thrown < ManticoreSpec.PAIR_DELAY
        }
    }
}

class ColosseumEnemies
@Inject
internal constructor(
    private val deps: BossDeps,
    private val waves: ColosseumWaves,
    private val modifiers: ColosseumModifiers,
    private val throws: ManticoreThrows,
    private val spells: MagicSpellRegistry,
    private val rayCast: RayCastValidator,
    private val sand: ColosseumSand,
) : PluginScript() {
    private val specs = ColosseumEnemySpecs(deps, ::healTarget)
    private val manticore = ManticoreSpec(deps, throws)
    private val warbandSlots = WarbandSlots(deps.collision)

    private val minotaurIds by lazy { MINOTAUR_TYPES.map { it.asRSCM(RSCMType.NPC) }.toSet() }

    private val routefinding by lazy {
        ColosseumEnemiesRow.all().filter { it.routefinds }.map { it.npc.id }.toSet()
    }

    override fun ScriptContext.startup() {
        for (spec in specs.all) {
            if (spec === specs.berserker) {
                BossCombat.register(this, spec, deps, onModifyHit = { magicAlwaysMaxes(npc, hit) })
            } else {
                BossCombat.register(this, spec, deps)
            }
        }
        BossCombat.register(this, manticore.spec, deps)
        registerHandlers()
        waves.onEnemySpawn(::onSpawn)
        waves.onEnemyDespawn { _, npc -> warbandSlots.remove(npc) }
        for (type in MINOTAUR_TYPES) onAiTimer(type) { updateMinotaurRange(npc) }
    }

    private fun onSpawn(npc: Npc) {
        npc.vars[ColosseumEnemyVarns.SPAWN_TICK] = deps.mapClock.cycle
        npc.routefinds = npc.type.id in routefinding
        if (npc.type.id in minotaurIds) npc.aiTimer(1)
        if (npc.type.id in warbandSlots.npcIds) {
            npc.chaseSlot = warbandSlots
            warbandSlots.add(npc)
            npc.defaultMoveSpeed = MoveSpeed.Run
            npc.delay = deps.mapClock.cycle + WARBAND_MOVE_DELAY
        }
        val firstAttack =
            when (npc.type.id) {
                id(ColosseumNpcs.MANTICORE) -> ManticoreSpec.FIRST_CHARGE_DELAY
                id(ColosseumNpcs.SHAMAN),
                id(ColosseumNpcs.JAVELIN),
                id(ColosseumNpcs.SHOCKWAVE) -> RANGED_FIRST_ATTACK_DELAY
                else -> return
            }
        deps.suppressAttacks(npc, firstAttack)
    }

    private fun registerHandlers() {
        val registry = deps.extensionRegistry
        registry.register(ColosseumEnemyHandlers.ARTILLERY_MARK) { ctx ->
            val tile = ctx.tile ?: return@register
            val reentry = modifiers.level(ctx.target, Modifier.Reentry) > 0
            val marker = if (reentry) REENTRY_MARKER else ARTILLERY_MARKER
            deps.worldRepo.spotanimMap(spotanim(marker), tile, delay = MARK_DELAY)
        }
        registry.register(ColosseumEnemyHandlers.ARTILLERY_LAND) { ctx ->
            val tile = ctx.tile ?: return@register
            artilleryImpact(ctx.npc, ctx.target, tile)
        }
        registry.register(ColosseumEnemyHandlers.MINOTAUR_HEAL) { ctx -> healAllies(ctx.npc) }
        registry.register(ColosseumEnemyHandlers.MINOTAUR_CRUSH) { ctx ->
            strike(ctx.npc, ctx.target, ColosseumEnemySpecs.MINOTAUR_MAX, MeleeAttackType.Crush)
        }
        registry.register(ColosseumEnemyHandlers.JAGUAR_CLAWS) { ctx ->
            repeat(ColosseumEnemySpecs.JAGUAR_HITS) {
                strike(ctx.npc, ctx.target, ColosseumEnemySpecs.JAGUAR_MAX, MeleeAttackType.Slash)
            }
        }
        registry.register(ColosseumEnemyHandlers.PICK_ORDER) { ctx ->
            pickOrder(ctx.npc, ctx.target)
        }
        registry.register(ColosseumEnemyHandlers.CLEAR_ORB) { ctx ->
            val slot = ctx.params as? Int ?: return@register
            ctx.npc.infoProtocol.setSpotanim(-1, 0, 0, slot)
        }
        registry.register(ColosseumEnemyHandlers.MARK_THROW) { ctx -> throws.mark(ctx.npc) }
        registry.register(ColosseumEnemyHandlers.VENOM) { ctx -> PlayerVenom.tryVenom(ctx.target) }
    }

    private fun artilleryImpact(npc: Npc, player: Player, tile: CoordGrid) {
        val onTile = player.coords == tile
        if (onTile) {
            val damage = deps.random.of(ARTILLERY_MIN_DAMAGE..ARTILLERY_MAX_DAMAGE)
            player.queueHit(npc, 1, HitType.Typeless, damage, deps.playerHitModifier)
        } else {
            deps.worldRepo.soundArea(tile, ARTILLERY_MISS_SYNTH, radius = ARTILLERY_SOUND_RADIUS)
        }
        val level = modifiers.level(player, Modifier.Reentry)
        val state = waves.stateOf(npc) ?: return
        if (level > 0) reentrySand(state, player, tile, level)
    }

    private fun reentrySand(state: WaveState, player: Player, impact: CoordGrid, level: Int) {
        if (sand.has(state, impact) || deps.collision.isWalkBlocked(impact)) return
        val permanent = level >= PERMANENT_LEVEL
        sand.add(state, player, impact, REENTRY_POOL, permanent)
        val synth = REENTRY_SYNTHS[(level - 1).coerceIn(0, REENTRY_SYNTHS.lastIndex)]
        deps.worldRepo.soundArea(
            impact,
            synth,
            delay = REENTRY_SOUND_DELAY,
            radius = REENTRY_SOUND_RADIUS,
        )
        val count = PoolRules.neighbours(level)
        if (count == 0) return
        deps.worldQueues.add(REENTRY_SPREAD_DELAY) {
            if (waves.stateOf(player) !== state) return@add
            val usable = { tile: CoordGrid ->
                !deps.collision.isWalkBlocked(tile) && !sand.has(state, tile)
            }
            for (tile in PoolRules.pick(impact, count, usable)) {
                val loc = REENTRY_SPREAD_POOLS[deps.random.of(REENTRY_SPREAD_POOLS.size)]
                val angle = LocAngle[deps.random.of(LOC_ANGLES)]
                sand.add(state, player, tile, loc, permanent, angle, LocShape.GroundDecor)
            }
        }
    }

    private fun pickOrder(npc: Npc, target: Player) {
        if (npc.vars[ColosseumEnemyVarns.ORBS[0]] != 0) return
        val partner =
            waves.stateOf(npc)?.enemies?.firstOrNull {
                it !== npc && it.type.id == npc.type.id && it.vars[ColosseumEnemyVarns.ORBS[0]] != 0
            }
        val order =
            if (partner != null) {
                ColosseumEnemyVarns.ORBS.map { partner.vars[it] - 1 }
            } else {
                val anyOrder =
                    target.vars[ManticoreSpec.MANTIMAYHEM_VARBIT] >= ManticoreSpec.ANY_ORDER_LEVEL
                val choices = if (anyOrder) ALL_ORDERS else MELEE_LAST_ORDERS
                choices[deps.random.of(choices.size)]
            }
        ColosseumEnemyVarns.ORBS.forEachIndexed { slot, varn -> npc.vars[varn] = order[slot] + 1 }
    }

    private fun strike(npc: Npc, target: Player, base: Int, type: MeleeAttackType) {
        if (npc.hitpoints <= 0) return
        val damage = ColosseumDamage.rollMelee(deps, npc, target, base, type)
        target.queueHit(npc, 1, HitType.Melee, damage, deps.playerHitModifier)
    }

    private fun healAllies(minotaur: Npc) {
        val ally = healTarget(minotaur) ?: return
        minotaur.anim(HEAL_SEQ)
        ally.spotanim(
            HEAL_SPOTANIM,
            height = if (ally.size == 1) HEAL_HEIGHT_SMALL else HEAL_HEIGHT_LARGE,
        )
        deps.hitpointsRestorer.heal(
            ally,
            ally.baseHitpointsLvl - ally.hitpoints,
            showHitsplat = true,
        )
    }

    private fun updateMinotaurRange(minotaur: Npc) {
        val player = waves.stateOf(minotaur)?.let(waves::playerOf)
        val ready = player != null && minotaurAttackReady(minotaur)
        val heals = ready && healTarget(minotaur) != null && !minotaur.isWithinDistance(player, 1)
        minotaur.apRangeOverride = if (heals) HEAL_TRIGGER_RANGE else null
    }

    private fun minotaurAttackReady(minotaur: Npc): Boolean {
        val encounter = deps.encounterRegistry.of(minotaur)
        return deps.mapClock.cycle >=
            (encounter.nextAttackTick ?: (encounter.lastAbilityTick + MINOTAUR_ATTACK_RATE))
    }

    private fun healTarget(minotaur: Npc): Npc? {
        if (minotaur.hitpoints <= 0) return null
        val state = waves.stateOf(minotaur) ?: return null
        val centre = minotaur.centre()
        return state.enemies.firstOrNull {
            it.type.id !in minotaurIds &&
                it.hitpoints > 0 &&
                it.hitpoints * HEAL_THRESHOLD_DENOMINATOR <
                    it.baseHitpointsLvl * HEAL_THRESHOLD_NUMERATOR &&
                it.centre().chebyshevDistance(centre) <= HEAL_RANGE &&
                rayCast.hasLineOfSight(centre, it.centre())
        }
    }

    private fun magicAlwaysMaxes(npc: Npc, hit: HitBuilder) {
        if (!hit.isFromPlayer || hit.type != HitType.Magic) return
        val attacker = hit.sourceSlot?.let { deps.playerList[it] } ?: return
        val spellObj = hit.secondaryType() ?: return
        val spell = spells.getObjSpell(spellObj) ?: return
        val range =
            deps.maxHit.getSpellMaxHitRange(
                attacker,
                npc,
                spellObj,
                spell.spellbook,
                spell.maxHit,
                SPELL_ATTACK_RATE,
                false,
            )
        hit.damage = maxOf(hit.damage, range.last)
    }

    private fun Npc.centre(): CoordGrid = coords.translate(size / 2, size / 2)

    private fun id(name: String): Int = name.asRSCM(RSCMType.NPC)

    private fun spotanim(name: String): SpotanimType = SpotanimType(name.asRSCM(RSCMType.SPOTANIM))

    private companion object {
        private const val WARBAND_MOVE_DELAY = 2
        private const val RANGED_FIRST_ATTACK_DELAY = 3
        private const val SPELL_ATTACK_RATE = 5

        private const val ARTILLERY_MARKER = "spotanim.npc_colossi_javelin_01_artillery_slow"
        private const val REENTRY_MARKER = "spotanim.npc_colossi_javelin_01_spearhead_fire_slow"
        private const val MARK_DELAY = 30

        private const val REENTRY_POOL = "loc.colosseum_molten_pool_1"
        private val REENTRY_SPREAD_POOLS =
            listOf("loc.colosseum_molten_pool_2", "loc.colosseum_molten_pool_3")
        private const val REENTRY_SPREAD_DELAY = 1
        private const val LOC_ANGLES = 4
        private const val PERMANENT_LEVEL = 2
        private const val ARTILLERY_MIN_DAMAGE = 15
        private const val ARTILLERY_MAX_DAMAGE = 45
        private const val ARTILLERY_MISS_SYNTH = "synth.colosseum_artillery_land"
        private const val ARTILLERY_SOUND_RADIUS = 15
        private val REENTRY_SYNTHS =
            listOf("synth.colosseum_reentry_pool", "synth.colosseum_reentry_pool_2")
        private const val REENTRY_SOUND_DELAY = 1
        private const val REENTRY_SOUND_RADIUS = 15

        private const val HEAL_SEQ = "seq.npc_minotaur_boss_attack_magic"
        private const val HEAL_RANGE = 6
        private const val HEAL_TRIGGER_RANGE = 15
        private const val MINOTAUR_ATTACK_RATE = 5
        private val MINOTAUR_TYPES =
            listOf(ColosseumNpcs.MINOTAUR, ColosseumNpcs.MINOTAUR_ROUTEFIND)
        private const val HEAL_SPOTANIM = "spotanim.tzhaar_heal"
        private const val HEAL_HEIGHT_SMALL = 64
        private const val HEAL_HEIGHT_LARGE = 128
        private const val HEAL_THRESHOLD_NUMERATOR = 3
        private const val HEAL_THRESHOLD_DENOMINATOR = 4

        private val MAGIC = ManticoreOrb.Magic.ordinal
        private val RANGED = ManticoreOrb.Ranged.ordinal
        private val MELEE = ManticoreOrb.Melee.ordinal
        private val MELEE_LAST_ORDERS =
            listOf(listOf(RANGED, MAGIC, MELEE), listOf(MAGIC, RANGED, MELEE))
        private val ALL_ORDERS =
            listOf(
                listOf(RANGED, MAGIC, MELEE),
                listOf(MAGIC, RANGED, MELEE),
                listOf(RANGED, MELEE, MAGIC),
                listOf(MAGIC, MELEE, RANGED),
                listOf(MELEE, RANGED, MAGIC),
                listOf(MELEE, MAGIC, RANGED),
            )
    }
}
