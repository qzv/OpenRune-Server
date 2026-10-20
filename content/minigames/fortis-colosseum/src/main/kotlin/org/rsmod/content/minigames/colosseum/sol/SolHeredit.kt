package org.rsmod.content.minigames.colosseum.sol

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.suppressAttacks
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.combat.commons.player.PlayerMaxHitHook
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.cinematic.Cinematic
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.hook.PlayerWornButtonHook
import org.rsmod.api.player.lockOverheads
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.output.CamShakeAxis
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.table.ColosseumSpawnsRow
import org.rsmod.content.minigames.colosseum.ColosseumArena
import org.rsmod.content.minigames.colosseum.ColosseumGlory
import org.rsmod.content.minigames.colosseum.ColosseumModifiers
import org.rsmod.content.minigames.colosseum.ColosseumNpcs
import org.rsmod.content.minigames.colosseum.ColosseumRun
import org.rsmod.content.minigames.colosseum.ColosseumWaves
import org.rsmod.content.minigames.colosseum.Modifier
import org.rsmod.content.minigames.colosseum.SolPet
import org.rsmod.content.minigames.colosseum.WaveState
import org.rsmod.content.minigames.colosseum.enemies.ColosseumDamage
import org.rsmod.content.minigames.colosseum.killTimeBest
import org.rsmod.content.minigames.colosseum.killTimeLatest
import org.rsmod.content.minigames.colosseum.lastWaveDuration
import org.rsmod.content.minigames.colosseum.modifiers.VolatilityModifier
import org.rsmod.content.minigames.colosseum.solKills
import org.rsmod.content.minigames.colosseum.totalDuration
import org.rsmod.content.minigames.colosseum.waveStartTime
import org.rsmod.content.other.pets.PetRewards
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private var Player.grapplePending by boolVarBit("varbit.colosseum_sol_grapple_pending")
private var Player.parryTick by intVarp("varp.colosseum_sol_parry_tick")
private var Player.parryUsedTick by intVarp("varp.colosseum_sol_parry_used_tick")
private var Player.protectFromMelee by intVarBit("varbit.prayer_protectfrommelee")
private var Player.solFailures by intVarBit("varbit.colosseum_sol_failures")
private var Player.bossCutsceneSeen by boolVarBit("varbit.colosseum_boss_cutscene_seen")

@Singleton
internal class SolGrappleHook @Inject constructor(private val fight: SolFight) :
    PlayerWornButtonHook {
    override fun intercept(player: Player, wornSlot: Int, op: IfButtonOp): Boolean {
        if (op != IfButtonOp.Op1) return false
        val state = fight.ofPlayer(player) ?: return false
        val slot = state.grappleSlot ?: return false
        if (wornSlot != slot.wornSlot) {
            player.mes(WRONG_SLOT_MESSAGE)
            return true
        }
        state.grappleSlot = null
        player.grapplePending = false
        player.anim(PARRY_SEQ)
        if (fight.now() - state.grappleStart >= SolFight.GRAPPLE_WINDOW) {
            player.mes("<col=229628>You perfectly parry Sol Heredit's grapple!")
            player.parryTick = fight.now()
        } else {
            player.mes(
                "<col=229628>You successfully defend your ${slot.displayName} from Sol " +
                    "Heredit's grapple!</col>"
            )
        }
        return true
    }

    companion object {
        const val PARRY_SEQ = "seq.warguild_parry_defend"
        const val GRAPPLE_HIT_SYNTH = "synth.colosseum_sol_grapple_hit"
        const val WRONG_SLOT_MESSAGE = "<col=ff3045>You defended the wrong body part!</col>"
    }
}

internal class SolParryMaxHitHook @Inject constructor(private val fight: SolFight) :
    PlayerMaxHitHook {
    override fun forceMaxHit(player: Player, target: PathingEntity): Boolean {
        val state = fight.ofPlayer(player) ?: return false
        if (target !== state.sol) return false
        return SolRules.parryBuffActive(player.parryTick, player.parryUsedTick, fight.now())
    }

    override fun onForcedMaxHit(player: Player, target: PathingEntity) {
        player.parryUsedTick = fight.now()
    }
}

internal fun failGrapple(fight: SolFight, state: SolState, slot: SolGrappleSlot) {
    val player = state.player
    val worn = player.worn[slot.wornSlot]
    val name = worn?.let { ServerCacheManager.getItem(it.id)?.name } ?: slot.displayName
    player.mes(
        "<col=ff3045>Sol Heredit grabs hold of your <col=00e6e6>$name</col> " +
            "<col=ff3045>and deals massive damage!</col>"
    )
    if (player.hitpoints <= 0) return
    player.soundSynth(SolGrappleHook.GRAPPLE_HIT_SYNTH)
    val damage = fight.rollUpTo(ColosseumDamage.maxHit(player, SolFight.GRAPPLE_MAX))
    fight.hit(state, damage, direct = true)
}

class SolHeredit
@Inject
internal constructor(
    private val deps: BossDeps,
    private val fight: SolFight,
    private val run: ColosseumRun,
    private val waves: ColosseumWaves,
    private val modifiers: ColosseumModifiers,
    private val volatility: VolatilityModifier,
    private val pets: PetRewards,
    private val hpBar: BossHpBarScript,
    private val interactions: AiPlayerInteractions,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val worldQueues: WorldQueueList,
    private val random: GameRandom,
    private val eventBus: EventBus,
) : PluginScript() {
    private val seats = mutableMapOf<WaveState, Npc>()

    private fun stateOf(npc: Npc): SolState? = fight.of(npc)

    private val transitionDue =
        Condition.Custom { npc, _ -> stateOf(npc)?.let(fight::transitionDue) == true }
    private val fast =
        Condition.Custom { npc, _ -> (stateOf(npc)?.phase ?: 0) >= SolFight.FAST_PHASE }
    private val spearForced = Condition.Custom { npc, _ -> stateOf(npc)?.forceSpear == true }

    private fun specialIs(special: SolSpecial): Condition =
        Condition.Custom { npc, _ -> stateOf(npc)?.let(fight::specialDue) == special }

    private val lateTriple =
        Condition.Custom { npc, _ -> SolRules.lateTriple(stateOf(npc)?.phase ?: 0) }

    private val spec: BossSpec =
        boss(ColosseumNpcs.SOL) {
            stats(attackRate = 1)
            val transition =
                ability("transition", external(TRANSITION), attackDelay = TRANSITION_DELAY)
            val spearSlow =
                ability("spear_slow", sequence(anim(SPEAR_SEQ), external(SPEAR)), attackDelay = 7)
            val spearFast =
                ability("spear_fast", sequence(anim(SPEAR_SEQ), external(SPEAR)), attackDelay = 6)
            val shieldSlow =
                ability(
                    "shield_slow",
                    sequence(anim(SHIELD_SEQ), external(SHIELD)),
                    attackDelay = 6,
                )
            val shieldFast =
                ability(
                    "shield_fast",
                    sequence(anim(SHIELD_SEQ), external(SHIELD)),
                    attackDelay = 5,
                )
            val grapple = ability("grapple", external(GRAPPLE), attackDelay = 7)
            val tripleShort =
                ability(
                    "triple_short",
                    sequence(
                        anim(TRIPLE_SHORT_SEQ),
                        spotanim(TRIPLE_SHORT_SPOTANIM),
                        external(TRIPLE),
                    ),
                    attackDelay = 11,
                )
            val tripleShortSlow =
                ability(
                    "triple_short_slow",
                    sequence(
                        anim(TRIPLE_SHORT_SEQ),
                        spotanim(TRIPLE_SHORT_SPOTANIM),
                        external(TRIPLE),
                    ),
                    attackDelay = 12,
                )
            val triple =
                ability(
                    "triple",
                    sequence(anim(TRIPLE_SEQ), spotanim(TRIPLE_SPOTANIM), external(TRIPLE)),
                    attackDelay = 12,
                )
            phase("fight") {
                forceWhen(transitionDue, transition)
                forceWhen(spearForced and WithinMeleeRange and fast, spearFast)
                forceWhen(spearForced and WithinMeleeRange and !fast, spearSlow)
                forceWhen(WithinMeleeRange and specialIs(SolSpecial.Triple) and lateTriple, triple)
                forceWhen(
                    WithinMeleeRange and specialIs(SolSpecial.Triple) and !lateTriple and fast,
                    tripleShort,
                )
                forceWhen(
                    WithinMeleeRange and specialIs(SolSpecial.Triple) and !lateTriple and !fast,
                    tripleShortSlow,
                )
                forceWhen(WithinMeleeRange and specialIs(SolSpecial.Grapple), grapple)
                weightedSelectorRandom {
                    +random(spearSlow, weight = 1, requires = WithinMeleeRange and !fast)
                    +random(shieldSlow, weight = 1, requires = WithinMeleeRange and !fast)
                    +random(spearFast, weight = 1, requires = WithinMeleeRange and fast)
                    +random(shieldFast, weight = 1, requires = WithinMeleeRange and fast)
                }
            }
        }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)
        registerHandlers()
        run.solStarter = { intro() }
        waves.onOpen(::seatSol)
        waves.onClose {
            seats.remove(it)
            fight.endFor(it)
        }
        onPlayerQueue(EAGER_QUEUE) { eagerCheck() }
        onPlayerQueueWithArgs<Int>(TRIPLE_HIT_QUEUE) { tripleHit(it.args) }
        onPlayerQueue(GRAPPLE_QUEUE) { resolveGrapple() }
        val type = checkNotNull(ServerCacheManager.getNpc(ColosseumNpcs.SOL.asRSCM(RSCMType.NPC)))
        onNpcQueue(type, DEATH_QUEUE) { death() }
    }

    private fun seatSol(state: WaveState) {
        val seated = waves.spawnStatic(state, SEATED, ColosseumArena.SEAT) ?: return
        waves.resolve(state, ColosseumArena.SEAT.translateZ(-1))?.let { seated.faceSquare(it) }
        waves.resolve(state, ColosseumArena.SEAT)?.let {
            locRepo.add(
                it,
                SEAT_BLOCKER,
                Int.MAX_VALUE,
                LocAngle.West,
                LocShape.CentrepieceStraight,
            )
        }
        seats[state] = seated
    }

    private fun registerHandlers() {
        val registry = deps.extensionRegistry
        registry.register(TRANSITION) { ctx -> stateOf(ctx.npc)?.let(::transition) }
        registry.register(SPEAR) { ctx -> stateOf(ctx.npc)?.let { fight.slam(it, SolStyle.Spear) } }
        registry.register(SHIELD) { ctx ->
            stateOf(ctx.npc)?.let { fight.slam(it, SolStyle.Shield) }
        }
        registry.register(TRIPLE) { ctx -> stateOf(ctx.npc)?.let(::startTriple) }
        registry.register(GRAPPLE) { ctx -> stateOf(ctx.npc)?.let(::startGrapple) }
    }

    private fun transition(state: SolState) {
        state.transitionPending = false
        val line = SolFight.PHASE_LINES[state.phase]
        state.phase++
        say(state, line)
        state.sol.faceSquare(state.player.coords)
        state.forceSpear = true
        if (state.phase == 1) fight.startSpecialCooldown(state)
        if (state.phase == SolFight.ENRAGE_PHASE) fight.enrage(state)
        worldQueues.add(1) { if (!state.dead) fight.strikeSand(state) }
        if (state.phase <= SolFight.CRYSTAL_PHASES) {
            worldQueues.add(2) { if (!state.dead) fight.spawnCrystal(state, state.phase - 1) }
        }
    }

    private fun startTriple(state: SolState) {
        fight.specialStarted(state)
        val damages = SolRules.tripleDamages(state.phase)
        val offsets = SolRules.tripleOffsets(state.phase)
        for ((index, offset) in offsets.withIndex()) {
            state.player.strongQueue(EAGER_QUEUE, offset)
            state.player.strongQueue(TRIPLE_HIT_QUEUE, offset + 1, damages[index])
        }
    }

    private fun ProtectedAccess.eagerCheck() {
        if (fight.ofPlayer(player) == null || player.protectFromMelee == 0) return
        player.lockOverheads(EAGER_LOCK_TICKS, singlePrayerRefusal = true)
        mes(EAGER_MESSAGE, ChatType.Spam)
    }

    private fun ProtectedAccess.tripleHit(base: Int) {
        val state = fight.ofPlayer(player) ?: return
        val damage = if (player.protectFromMelee != 0) 0 else ColosseumDamage.maxHit(player, base)
        fight.hit(state, damage, direct = true)
    }

    private fun startGrapple(state: SolState) {
        fight.specialStarted(state)
        val slot = SolGrappleSlot.entries[random.of(SolGrappleSlot.entries.size)]
        state.grappleSlot = slot
        state.grappleStart = fight.now()
        state.player.grapplePending = true
        state.sol.anim(GRAPPLE_SEQ, GRAPPLE_SEQ_DELAY)
        fight.grappleMessage(state, slot)
        state.player.strongQueue(GRAPPLE_QUEUE, SolFight.GRAPPLE_WINDOW + 1)
    }

    private fun ProtectedAccess.resolveGrapple() {
        val state = fight.ofPlayer(player) ?: return
        val slot = state.grappleSlot ?: return
        state.grappleSlot = null
        player.grapplePending = false
        failGrapple(fight, state, slot)
    }

    private suspend fun ProtectedAccess.intro() {
        val wave = waves.stateOf(player) ?: return
        val seated = seats.remove(wave)
        val cutscene = !player.bossCutsceneSeen
        if (cutscene) firstMeeting(wave)
        seated?.anim(JUMP_SEQ)
        delay(1)
        mes("<col=ff3045>Sol Heredit jumps down from his seat...</col>")
        fadeOverlay(0, FADE_CLEAR, 0, FADE_OPAQUE, FADE_CYCLES)
        delay(1)
        minimapHideMap()
        delay(2)
        val start = waves.resolve(wave, ColosseumArena.SOL_PLAYER_START)
        start?.let {
            val hop = it.translateLevel(HOP_LEVEL)
            deps.collision.allocateIfAbsent(hop.x, hop.z, hop.level)
            telejump(hop)
        }
        seated?.let { if (it.isSlotAssigned) npcRepo.del(it, Int.MAX_VALUE) }
        delay(1)
        start?.let {
            telejump(it)
            faceSquare(it.translateZ(1))
        }
        waves.resolve(wave, ColosseumArena.SEAT)?.let {
            locRepo.add(it, THRONE, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
        }
        if (cutscene) endFirstMeeting()
        camForceAngle(CAM_ANGLE, 0)
        delay(1)
        val sol = waves.spawnStatic(wave, ColosseumNpcs.SOL, ColosseumArena.SOL_SPAWN) ?: return
        val state = fight.start(sol, player, wave) ?: return
        run.solSpawned(player, wave, sol)
        sol.anim(LAND_SEQ)
        sol.spotanim(LAND_SPOTANIM, delay = LAND_SPOTANIM_DELAY)
        sol.faceSquare(player.coords)
        fight.placeWalls(state)
        fadeOverlay(0, FADE_OPAQUE, 0, FADE_CLEAR, FADE_CYCLES)
        minimapReset()
        deps.suppressAttacks(sol, FIRST_ATTACK_DELAY)
        sol.apPlayer2(player, interactions)
        tick(state)
        delay(1)
        for (axis in CamShakeAxis.entries.take(SHAKE_AXES)) camShake(axis, SHAKE_RANDOM, 0, 0)
        delay(1)
        hpBar.onOpen(player, sol)
        delay(1)
        say(state, SolFight.OPENING_LINE)
        if (modifiers.owns(player, Modifier.Quartet)) spawnQuartet(player, wave)
        delay(1)
        camReset()
    }

    private suspend fun ProtectedAccess.firstMeeting(wave: WaveState) {
        player.bossCutsceneSeen = true
        fadeOverlay(0, FADE_CLEAR, 0, FADE_OPAQUE, FADE_CYCLES)
        Cinematic.clearHealthHud(player)
        delay(CUTSCENE_SETUP_DELAY)
        player.disablePrayers()
        camModeFixed()
        Cinematic.setHideToplevel(player, true)
        Cinematic.setHideEntityOps(player, true)
        minimapHideMap()
        Cinematic.setHideHealthHud(player, true)
        Cinematic.closeToplevelTabsLenient(player, eventBus)
        val (eyeStart, eyeEnd, focus) = ColosseumArena.coords(CUTSCENE_ROW)
        waves.resolve(wave, eyeStart)?.let { camMoveTo(it, CUTSCENE_EYE_HEIGHT, 232, 100) }
        waves.resolve(wave, focus)?.let { camLookAt(it, CUTSCENE_FOCUS_HEIGHT, 232, 232) }
        delay(1)
        fadeOverlay(0, FADE_OPAQUE, 0, FADE_CLEAR, FADE_CYCLES)
        waves.resolve(wave, eyeEnd)?.let { camMoveTo(it, CUTSCENE_EYE_HEIGHT, 7, 0) }
        delay(CUTSCENE_DIALOGUE_DELAY)
        val neutral = checkNotNull(ServerCacheManager.getMesAnim("mesanim.neutral".asRSCM()))
        for (line in MEETING_LINES) chatNpcSpecific("Sol Heredit", ColosseumNpcs.SOL, neutral, line)
    }

    private fun ProtectedAccess.endFirstMeeting() {
        camModeReset()
        Cinematic.setHideToplevel(player, false)
        Cinematic.setHideEntityOps(player, false)
        Cinematic.setHideHealthHud(player, false)
        Cinematic.openTopLevelTabs(player, eventBus)
    }

    private fun spawnQuartet(player: Player, wave: WaveState) {
        val type = QUARTET_TYPES[random.of(QUARTET_TYPES.size)]
        val tiles = ColosseumSpawnsRow.getRow(QUARTET_ROW).coords
        val tile = tiles[random.of(tiles.size)]
        val before = wave.enemies.toSet()
        waves.spawnWaveEnemy(player, wave, type, tile)
        for (npc in wave.enemies - before) {
            wave.enemies -= npc
            waves.trackExtra(wave, npc)
        }
    }

    private fun tick(state: SolState) {
        worldQueues.add(1) {
            if (state.dead) return@add
            if (state.player.hitpoints <= 0) {
                onPlayerDeath(state)
                return@add
            }
            fight.tick(state)
            tick(state)
        }
    }

    private fun onPlayerDeath(state: SolState) {
        val player = state.player
        fight.end(state)
        state.grappleSlot = null
        player.grapplePending = false
        deps.suppressAttacks(state.sol, PLAYER_DEATH_SUPPRESS)
        fight.despawnCrystals(state)
        fight.clearSand(state)
        worldQueues.add(1) {
            if (state.sol.isSlotAssigned)
                say(state, SolFight.PLAYER_DEATH_LINES[random.of(SolFight.PLAYER_DEATH_LINES.size)])
        }
        worldQueues.add(FAILURE_DELAY) { player.solFailures += 1 }
    }

    private suspend fun StandardNpcAccess.death() {
        val state = fight.of(npc)
        state?.let { volatility.onEnemyDeath(it.wave, npc) }
        noneMode()
        hideAllOps()
        anim(DEATH_SEQ)
        spotanim(DEATH_SPOTANIM)
        if (state != null) onKill(state)
        delay(1)
        state?.let { say(it, SolFight.VICTORY_LINES[random.of(SolFight.VICTORY_LINES.size)]) }
        delay(DESPAWN_DELAY - 1)
        state?.let { volatility.onEnemyDespawn(it.wave, npc) }
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    private fun onKill(state: SolState) {
        val player = state.player
        fight.end(state)
        state.grappleSlot = null
        player.grapplePending = false
        fight.despawnCrystals(state)
        fight.clearSand(state)
        player.solKills += 1
        player.mes("Your Sol Heredit kill count is: <col=ff0000>${player.solKills}</col>.")
        val total = player.totalDuration + (fight.now() - player.waveStartTime)
        val best = player.killTimeBest
        val newBest = best == 0 || total < best
        player.killTimeLatest = total
        if (newBest) player.killTimeBest = total
        val volatile = modifiers.owns(player, Modifier.Volatility)
        val deferred = if (volatile) deferFlares(state) else emptyList()
        run.completeWave(player, state.wave)
        val shown = ColosseumGlory.formatDuration(player, total)
        player.mes(
            if (newBest) {
                "Colosseum duration: <col=ff3045>$shown</col> (new personal best)"
            } else {
                "Colosseum duration: <col=ff3045>$shown</col>. " +
                    "Personal best: ${ColosseumGlory.formatDuration(player, best)}"
            }
        )
        player.totalDuration += player.lastWaveDuration
        if (random.of(SolPet.rate) == 0) pets.give(player, SolPet.obj)
        val finishDelay = if (volatile) FINISH_DELAY + GIB_CHEST_EXTRA else FINISH_DELAY
        worldQueues.add(finishDelay) {
            run.finish(player, CHEST_DELAY)
            player.midiJingle(VICTORY_JINGLE)
        }
        if (volatile) {
            worldQueues.add(FLARE_DELETE_DELAY) {
                deferred.forEach { if (it.isSlotAssigned) npcRepo.del(it, Int.MAX_VALUE) }
            }
        }
    }

    private fun deferFlares(state: SolState): List<Npc> {
        val type = ColosseumNpcs.SOLAR_FLARE.asRSCM(RSCMType.NPC)
        val flares = state.wave.extras.filter { it.type.id == type }
        state.wave.extras.removeAll(flares.toSet())
        return flares
    }

    private fun say(state: SolState, text: String) {
        state.sol.say(text)
        state.player.mes("$NAME|$text", ChatType.NpcSay)
    }

    private companion object {
        const val SEATED = "npc.colosseum_boss_seated"
        const val NAME = "Sol Heredit"
        const val SEAT_BLOCKER = "loc.invisible_type8_blocking_size4"
        const val THRONE = "loc.throne_civitas_colosseum01_big01"
        const val VICTORY_JINGLE = "jingle.inadequacy"

        const val TRANSITION = "sol.transition"
        const val SPEAR = "sol.spear"
        const val SHIELD = "sol.shield"
        const val TRIPLE = "sol.triple"
        const val GRAPPLE = "sol.grapple"

        const val EAGER_QUEUE = "queue.colosseum_sol_eager"
        const val TRIPLE_HIT_QUEUE = "queue.colosseum_sol_triple_hit"
        const val GRAPPLE_QUEUE = "queue.colosseum_sol_grapple"
        const val DEATH_QUEUE = "queue.death"

        const val SPEAR_SEQ = "seq.npc_colossi_finalboss_01_melee_attack_telegraph"
        const val SHIELD_SEQ = "seq.npc_colossi_finalboss_01_shieldslam_telegraph"
        const val GRAPPLE_SEQ = "seq.npc_colossi_finalboss_01_grapple_attack_telegraph"
        const val GRAPPLE_SEQ_DELAY = 30
        const val TRIPLE_SEQ = "seq.npc_colossi_finalboss_tripleattack"
        const val TRIPLE_SPOTANIM = "spotanim.npc_colossi_colossi_tripleattack_01_telegraph"
        const val TRIPLE_SHORT_SEQ = "seq.npc_colossi_finalboss_tripleattack_shorter"
        const val TRIPLE_SHORT_SPOTANIM =
            "spotanim.npc_colossi_colossi_tripleattack_01_telegraph_shorter"
        const val JUMP_SEQ = "seq.npc_colossi_finalboss_01_arena_jump"
        const val LAND_SEQ = "seq.npc_colossi_finalboss_01_arena_land"
        const val LAND_SPOTANIM = "spotanim.npc_colossi_colossi_01_land"
        const val LAND_SPOTANIM_DELAY = 15
        const val DEATH_SEQ = "seq.npc_colossi_finalboss_01_death"
        const val DEATH_SPOTANIM = "spotanim.npc_colossi_finalboss_01_death"

        const val CUTSCENE_SETUP_DELAY = 2
        const val CUTSCENE_DIALOGUE_DELAY = 3
        const val CUTSCENE_EYE_HEIGHT = 1390
        const val CUTSCENE_FOCUS_HEIGHT = 990
        const val CUTSCENE_ROW = "dbrow.colosseum_sol_cutscene"
        val MEETING_LINES =
            listOf(
                "By Ralos, we finally have a worthy challenger.",
                "You've torn through multiple waves of combatants and you're still standing. " +
                    "I'm almost impressed.",
                "Now let's see how you handle a real foe...",
            )
        const val EAGER_MESSAGE =
            "<col=ff3045>Sol Heredit doesn't take kindly to your eager prayer.</col>"
        const val EAGER_LOCK_TICKS = 2
        const val TRANSITION_DELAY = 7
        const val FIRST_ATTACK_DELAY = 5
        const val DESPAWN_DELAY = 9
        const val FINISH_DELAY = 8
        const val CHEST_DELAY = 1
        const val FAILURE_DELAY = 6
        const val GIB_CHEST_EXTRA = 2
        const val FLARE_DELETE_DELAY = 11
        const val PLAYER_DEATH_SUPPRESS = 100
        val QUARTET_TYPES = listOf(ColosseumNpcs.BERSERKER, ColosseumNpcs.ARCHER)
        const val QUARTET_ROW = "dbrow.colosseum_sol_quartet"

        const val FADE_CLEAR = 255
        const val FADE_OPAQUE = 0
        const val FADE_CYCLES = 50
        const val CAM_ANGLE = 181
        const val HOP_LEVEL = 2
        const val SHAKE_AXES = 3
        const val SHAKE_RANDOM = 5
    }
}
