package org.rsmod.content.minigames.colosseum.sol

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.table.ColosseumSpawnsRow
import org.rsmod.content.minigames.colosseum.ColosseumArena
import org.rsmod.content.minigames.colosseum.ColosseumSand
import org.rsmod.content.minigames.colosseum.ColosseumWaves
import org.rsmod.content.minigames.colosseum.WaveState
import org.rsmod.content.minigames.colosseum.enemies.ColosseumDamage
import org.rsmod.content.minigames.colosseum.modifiers.BlasphemyModifier
import org.rsmod.content.minigames.colosseum.waveDamageTaken
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

internal enum class SolStyle {
    None,
    Spear,
    Shield,
}

internal class SolCrystal(val npc: Npc, val index: Int, val horizontal: Boolean) {
    var forward = false
    var nextStep = 0
}

internal class SolSandTile(val burnsFrom: Int) {
    var loc: LocInfo? = null
}

internal class SolState(val sol: Npc, val player: Player, val wave: WaveState) {
    var phase = 0
    var transitionPending = false
    var lastStyle = SolStyle.None
    var lastVariant = 0
    var forceSpear = false
    var lastSpecialTick = -1
    var specialCooldown = SolRules.FIRST_SPECIAL_COOLDOWN
    var specialRollTick = -1
    var rolledSpecial: SolSpecial? = null
    var grappleSlot: SolGrappleSlot? = null
    var grappleStart = 0
    val crystals = mutableListOf<SolCrystal>()
    val volleyCrystals = mutableListOf<SolCrystal>()
    var volleyCue = -1
    var volleyFireDelay = 0
    var volleyReady = Int.MAX_VALUE
    var forceVolley = false
    var pendingBeam: CoordGrid? = null
    var pendingBeamTick = -1
    val sand = mutableMapOf<CoordGrid, SolSandTile>()
    var enraged = false
    var enrageTick = -1
    var dead = false
}

internal enum class SolSpecial {
    Grapple,
    Triple,
}

internal enum class SolGrappleSlot(
    private val wearpos: Wearpos,
    val verb: String,
    val part: String,
    val suffix: String,
) {
    Back(Wearpos.Back, "BREAK", "BACK", ""),
    Body(Wearpos.Torso, "CRUSH", "BODY", ""),
    Legs(Wearpos.Legs, "BREAK", "LEGS", ""),
    Hands(Wearpos.Hands, "TWIST", "HANDS", " OFF"),
    Feet(Wearpos.Feet, "CUT", "FEET", " OFF");

    val wornSlot: Int
        get() = wearpos.slot

    val callout: String
        get() = "I'LL $verb YOUR <col=ffffff>$part<col=FF0000>$suffix!"

    val displayName: String
        get() = part.lowercase().replaceFirstChar { it.uppercase() }
}

@Singleton
internal class SolFight
@Inject
constructor(
    private val waves: ColosseumWaves,
    private val sand: ColosseumSand,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
    private val worldQueues: WorldQueueList,
    private val mapClock: MapClock,
    private val random: GameRandom,
    private val hitModifier: StandardPlayerHitModifier,
    private val blasphemy: BlasphemyModifier,
) {
    private val fights = mutableMapOf<Npc, SolState>()

    fun start(sol: Npc, player: Player, wave: WaveState): SolState? {
        waves.resolve(wave, ColosseumArena.SOL_SPAWN) ?: return null
        val state = SolState(sol, player, wave)
        fights[sol] = state
        return state
    }

    fun of(sol: Npc): SolState? = fights[sol]

    fun ofPlayer(player: Player): SolState? =
        fights.values.firstOrNull { it.player === player && !it.dead }

    fun end(state: SolState) {
        state.dead = true
        fights.remove(state.sol)
    }

    fun endFor(wave: WaveState) {
        for (state in fights.values.filter { it.wave === wave }) {
            end(state)
            (state.crystals + state.volleyCrystals).forEach {
                if (it.npc.isSlotAssigned) npcRepo.del(it.npc, Int.MAX_VALUE)
            }
            state.crystals.clear()
            state.volleyCrystals.clear()
        }
    }

    fun now(): Int = mapClock.cycle

    fun rollUpTo(max: Int): Int = maxOf(random.of(0, max), random.of(0, max))

    fun hit(state: SolState, damage: Int, direct: Boolean = false) {
        if (state.player.hitpoints <= 0) return
        val hit = state.player.queueHit(state.sol, 1, HitType.Typeless, damage, hitModifier)
        if (direct) worldQueues.add(1) { if (!state.dead) landDirect(state, hit) }
    }

    private fun landDirect(state: SolState, hit: Hit) {
        state.player.waveDamageTaken += hit.damage
        blasphemy.onHit(state.player, hit)
    }

    fun transitionDue(state: SolState): Boolean {
        if (state.transitionPending) return true
        val next = PHASE_THRESHOLDS.getOrNull(state.phase) ?: return false
        if (!SolRules.thresholdReached(state.sol.hitpoints, state.sol.baseHitpointsLvl, next))
            return false
        state.transitionPending = true
        return true
    }

    fun inArena(state: SolState, tile: CoordGrid): Boolean {
        val local = waves.templateOf(state.wave, tile) ?: return false
        val x = local.x
        val z = local.z
        if (x !in ARENA_MIN_X..ARENA_MAX_X || z !in ARENA_MIN_Z..ARENA_MAX_Z) return false
        val cornerX = x == ARENA_MIN_X || x == ARENA_MAX_X
        val cornerZ = z == ARENA_MIN_Z || z == ARENA_MAX_Z
        return !(cornerX && cornerZ)
    }

    fun resolve(state: SolState, template: CoordGrid): CoordGrid =
        waves.resolve(state.wave, template) ?: template

    fun nextVariant(state: SolState, style: SolStyle): Int {
        val variant =
            when {
                style == SolStyle.Spear && state.forceSpear ->
                    if (state.lastStyle == SolStyle.Spear && state.lastVariant == 1) 2 else 1
                state.lastStyle == style -> if (state.lastVariant == 1) 2 else 1
                else -> 1
            }
        state.forceSpear = false
        state.lastStyle = style
        state.lastVariant = variant
        return variant
    }

    fun slam(state: SolState, style: SolStyle) {
        val variant = nextVariant(state, style)
        state.sol.movementLocked = true
        onAttack(state)
        worldQueues.add(SLAM_IMPACT_TICKS) { resolveSlam(state, style, variant) }
        worldQueues.add(SLAM_LOCK_TICKS) { if (!state.dead) state.sol.movementLocked = false }
    }

    private fun resolveSlam(state: SolState, style: SolStyle, variant: Int) {
        if (state.dead) return
        val origin = state.sol.coords
        val player = state.player
        val dx = player.coords.x - origin.x
        val dz = player.coords.z - origin.z
        val inArena = { x: Int, z: Int -> inArena(state, origin.translate(x, z)) }
        val tiles =
            if (style == SolStyle.Spear)
                SolPatterns.spear(variant, SolPatterns.side(dx, dz), inArena)
            else SolPatterns.shield(variant, inArena)
        for (tile in tiles) {
            val gfx = SLAM_GRAPHICS[random.of(SLAM_GRAPHICS.size)]
            worldRepo.spotanimMap(
                spotanim(gfx),
                origin.translate(tile.dx, tile.dz),
                delay = tile.delay,
            )
        }
        val struck = tiles.any { it.dx == dx && it.dz == dz }
        if (struck) hit(state, rollUpTo(ColosseumDamage.maxHit(player, SLAM_MAX)), direct = true)
    }

    fun specialDue(state: SolState): SolSpecial? {
        val now = now()
        if (state.specialRollTick == now) return state.rolledSpecial
        state.specialRollTick = now
        state.rolledSpecial = null
        if (state.forceSpear || state.transitionPending) return null
        if (!SolRules.specialEligible(now, state.lastSpecialTick, state.specialCooldown))
            return null
        if (random.of(PERCENT) >= SolRules.SPECIAL_CHANCE_PERCENT) return null
        val special =
            if (state.phase >= GRAPPLE_PHASE && random.of(2) == 0) SolSpecial.Grapple
            else SolSpecial.Triple
        state.rolledSpecial = special
        return special
    }

    fun specialStarted(state: SolState) {
        state.rolledSpecial = null
        state.lastSpecialTick = now()
        state.specialCooldown = SolRules.SPECIAL_COOLDOWN
        state.lastStyle = SolStyle.None
        state.lastVariant = 0
        onAttack(state)
    }

    fun startSpecialCooldown(state: SolState) {
        if (state.lastSpecialTick < 0) state.lastSpecialTick = now()
    }

    fun strikeSand(state: SolState) {
        val centre = state.player.coords
        val rolls = List(SAND_ROLLS) { random.of(centre, SAND_RADIUS) }
        val picks =
            SolRules.sandPicks(centre, rolls) {
                !inArena(state, it) || it in state.sand || sand.has(state.wave, it)
            }
        applySand(state, picks)
    }

    private fun enrageSand(state: SolState) {
        val centre = state.player.coords
        val tile = random.of(centre, SAND_RADIUS)
        if (
            tile == centre ||
                !inArena(state, tile) ||
                tile in state.sand ||
                sand.has(state.wave, tile)
        )
            return
        applySand(state, listOf(tile))
    }

    private fun applySand(state: SolState, picks: List<CoordGrid>) {
        val player = state.player
        val strike = now()
        for (tile in picks) {
            worldRepo.spotanimMap(spotanim(SAND_STRIKE), tile, delay = SAND_STRIKE_DELAY)
            state.sand[tile] = SolSandTile(strike + SAND_BURN_TICKS)
        }
        player.soundSynth(SAND_STRIKE_SYNTH, delay = SAND_STRIKE_DELAY)
        player.soundSynth(SAND_RUMBLE_SYNTH, delay = SAND_RUMBLE_DELAY)
        worldQueues.add(SAND_LOC_TICKS) {
            if (state.dead) return@add
            for (tile in picks) {
                val entry = state.sand[tile] ?: continue
                entry.loc =
                    locRepo.add(
                        tile,
                        SAND_LOC,
                        Int.MAX_VALUE,
                        LocAngle[random.of(LOC_ANGLES)],
                        LocShape.CentrepieceStraight,
                    )
            }
        }
    }

    fun clearSand(state: SolState) {
        val locs = state.sand.values.mapNotNull { it.loc }
        state.sand.clear()
        locs.forEach { worldRepo.locAnim(it, SAND_DESPAWN_SEQ) }
        worldQueues.add(1) {
            if (open(state)) locs.forEach { worldRepo.locAnim(it, SAND_DESPAWN_SEQ) }
        }
        worldQueues.add(2) {
            if (!open(state)) return@add
            locs.forEach {
                locRepo.add(it.coords, SAND_CLEARED_LOC, Int.MAX_VALUE, it.angle, it.shape)
            }
        }
    }

    private fun open(state: SolState): Boolean = waves.playerOf(state.wave) != null

    fun spawnCrystal(state: SolState, index: Int) {
        val edge = ColosseumSpawnsRow.getRow(CRYSTAL_EDGES_ROW).coords.getOrNull(index) ?: return
        val horizontal = index % 2 == 0
        val offset = random.of(CRYSTAL_SPAWN_SPREAD)
        val template = if (horizontal) edge.translateX(offset) else edge.translateZ(offset)
        val npc = waves.spawnStatic(state.wave, CRYSTAL, template) ?: return
        npc.anim(CRYSTAL_SPAWN_SEQ)
        val crystal = SolCrystal(npc, index, horizontal)
        crystal.nextStep = now() + random.of(CRYSTAL_FIRST_STEP.first, CRYSTAL_FIRST_STEP.last)
        if (state.crystals.isEmpty() && state.volleyCrystals.isEmpty()) state.forceVolley = true
        state.crystals += crystal
    }

    fun enrage(state: SolState) {
        state.enraged = true
        state.enrageTick = now()
        state.forceVolley = true
    }

    private fun onAttack(state: SolState) {
        if (state.dead || state.crystals.isEmpty() || state.volleyCue >= 0) return
        if (!state.forceVolley && now() < state.volleyReady) return
        val now = now()
        state.forceVolley = false
        state.volleyCue = now
        state.volleyFireDelay = if (state.enraged) ENRAGE_FIRE_DELAY else CRYSTAL_FIRE_DELAY
        state.volleyReady =
            now +
                if (state.enraged) ENRAGE_VOLLEY_COOLDOWN
                else random.of(VOLLEY_COOLDOWN.first, VOLLEY_COOLDOWN.last)
        state.volleyCrystals.clear()
        state.volleyCrystals += state.crystals
        state.player.soundSynth(CRYSTAL_LOCK_SYNTH, delay = CRYSTAL_LOCK_DELAY)
        val cueDelay = if (state.enraged) ENRAGE_CUE_DELAY else CRYSTAL_CUE_DELAY
        state.player.soundSynth(CRYSTAL_CUE_SYNTH, delay = cueDelay)
    }

    fun tick(state: SolState) {
        if (state.dead) return
        val now = now()
        burnSand(state, now)
        if (state.enraged && state.enrageTick >= 0) {
            val since = now - state.enrageTick - ENRAGE_SAND_FIRST
            if (since >= 0 && since % ENRAGE_SAND_INTERVAL == 0) enrageSand(state)
        }
        resolvePendingBeam(state, now)
        val cue = state.volleyCue
        if (cue >= 0) {
            val charge = cue + CUE_TO_CHARGE
            when (now) {
                charge -> chargeCrystals(state)
                charge + state.volleyFireDelay -> fireCrystals(state)
                charge + if (state.enraged) ENRAGE_CHARGE_TO_RESUME else CHARGE_TO_RESUME -> {
                    state.volleyCue = -1
                    state.crystals.forEach { it.nextStep = now }
                }
            }
        }
        if (state.volleyCue >= 0) return
        for (crystal in state.crystals) {
            if (now < crystal.nextStep) continue
            patrol(state, crystal)
            crystal.nextStep = now + CRYSTAL_STEP_TICKS
        }
    }

    private fun burnSand(state: SolState, now: Int) {
        val player = state.player
        val tile = state.sand[player.coords] ?: return
        if (now < tile.burnsFrom) return
        hit(state, random.of(SAND_DAMAGE))
    }

    private fun resolvePendingBeam(state: SolState, now: Int) {
        val tile = state.pendingBeam ?: return
        if (now < state.pendingBeamTick) return
        state.pendingBeam = null
        if (state.player.coords == tile) hit(state, random.of(BEAM_DAMAGE))
    }

    private fun patrol(state: SolState, crystal: SolCrystal) {
        val local = waves.templateOf(state.wave, crystal.npc.coords) ?: return
        val position = if (crystal.horizontal) local.x else local.z
        val (min, max) = if (crystal.horizontal) CRYSTAL_X_RANGE else CRYSTAL_Z_RANGE
        if (position >= max) crystal.forward = false
        if (position <= min) crystal.forward = true
        val step = if (crystal.forward) 1 else -1
        val next =
            if (crystal.horizontal) crystal.npc.coords.translateX(step)
            else crystal.npc.coords.translateZ(step)
        crystal.npc.walk(next)
    }

    private fun inward(crystal: SolCrystal): Pair<Int, Int> =
        when (crystal.index) {
            0 -> 0 to -1
            1 -> -1 to 0
            2 -> 0 to 1
            else -> 1 to 0
        }

    private fun beamTiles(state: SolState, crystal: SolCrystal): Pair<List<CoordGrid>, CoordGrid> {
        val start = crystal.npc.coords
        val (dx, dz) = inward(crystal)
        val length = SolRules.BEAM_LENGTH
        val line = (1..length).map { start.translate(dx * it, dz * it) }
        val wall = start.translate(dx * (length + 1), dz * (length + 1))
        return line.filter { inArena(state, it) } to wall
    }

    private fun chargeCrystals(state: SolState) {
        for (crystal in state.volleyCrystals) {
            if (!crystal.npc.isSlotAssigned) continue
            crystal.npc.anim(CRYSTAL_CHARGE_SEQ)
            val (line, _) = beamTiles(state, crystal)
            line.forEachIndexed { step, tile ->
                worldRepo.spotanimMap(
                    spotanim(CHARGE_BEAMS[crystal.index]),
                    tile,
                    BEAM_HEIGHT,
                    BEAM_STEP_DELAY * (step + 1),
                )
            }
        }
    }

    private fun fireCrystals(state: SolState) {
        val player = state.player.coords
        var closest: Int? = null
        for (crystal in state.volleyCrystals) {
            if (!crystal.npc.isSlotAssigned) continue
            crystal.npc.anim(CRYSTAL_ATTACK_SEQ)
            val (line, wall) = beamTiles(state, crystal)
            line.forEachIndexed { step, tile ->
                worldRepo.spotanimMap(
                    spotanim(ATTACK_BEAMS[crystal.index]),
                    tile,
                    BEAM_HEIGHT,
                    BEAM_STEP_DELAY * (step + 1),
                )
            }
            worldRepo.spotanimMap(
                spotanim(BEAM_IMPACT),
                wall,
                BEAM_HEIGHT,
                BEAM_STEP_DELAY * SolRules.BEAM_LENGTH,
            )
            worldRepo.soundArea(wall, BEAM_IMPACT_SYNTH, radius = BEAM_SOUND_RADIUS)
            if (player !in line) continue
            val (dx, dz) = inward(crystal)
            val distance = SolRules.beamDistance(crystal.npc.coords, dx, dz, player) ?: continue
            if (SolRules.beamDelay(distance) == null) continue
            if (closest == null || distance < closest) closest = distance
        }
        val distance = closest ?: return
        if (SolRules.beamDelay(distance) == 0) {
            hit(state, random.of(BEAM_DAMAGE))
        } else {
            state.pendingBeam = player
            state.pendingBeamTick = now() + 1
        }
    }

    fun despawnCrystals(state: SolState) {
        val crystals = (state.crystals + state.volleyCrystals).distinct()
        state.crystals.clear()
        state.volleyCrystals.clear()
        state.volleyCue = -1
        state.pendingBeam = null
        state.wave.extras.removeAll(crystals.map { it.npc }.toSet())
        for (crystal in crystals) crystal.npc.anim(CRYSTAL_DESPAWN_SEQ)
        worldQueues.add(1) {
            crystals.forEach { if (it.npc.isSlotAssigned) npcRepo.del(it.npc, Int.MAX_VALUE) }
        }
    }

    fun grappleMessage(state: SolState, slot: SolGrappleSlot) {
        state.player.mes("Sol Heredit: <col=ff3045>${slot.callout}")
        state.sol.say("<col=ff0000>${slot.callout}")
    }

    private fun spotanim(name: String): SpotanimType = SpotanimType(name.asRSCM(RSCMType.SPOTANIM))

    fun placeWalls(state: SolState) {
        val wallTiles =
            (ARENA_MIN_Z + 1 until ARENA_MAX_Z).map { CoordGrid(ARENA_MAX_X + 1, it, 0) to 1 } +
                (ARENA_MIN_X + 1 until ARENA_MAX_X).map { CoordGrid(it, ARENA_MAX_Z + 1, 0) to 0 } +
                (ARENA_MIN_Z + 1 until ARENA_MAX_Z).map { CoordGrid(ARENA_MIN_X - 1, it, 0) to 3 } +
                (ARENA_MIN_X + 1 until ARENA_MAX_X).map { CoordGrid(it, ARENA_MIN_Z - 1, 0) to 2 }
        for ((template, angle) in wallTiles) {
            val loc = WALLS[random.of(WALLS.size)]
            locRepo.add(
                resolve(state, template),
                loc,
                Int.MAX_VALUE,
                LocAngle[angle],
                LocShape.CentrepieceStraight,
            )
        }
    }

    companion object {
        val PHASE_THRESHOLDS = intArrayOf(90, 75, 50, 25, 10)
        val PHASE_LINES =
            listOf(
                "Not bad. Let's try something else...",
                "Impressive. Let's see how you handle this...",
                "You can't win!",
                "Ralos guides my hand!",
                "LET'S END THIS!",
            )
        const val OPENING_LINE = "Let's start by testing your footwork."
        val VICTORY_LINES =
            listOf(
                "Ralos has smiled upon you...",
                "My compliments, champion...",
                "A worthy... opponent...",
                "Your light shines... brightly...",
            )
        val PLAYER_DEATH_LINES =
            listOf(
                "Pathetic, really...",
                "Your lack of coordination is concerning.",
                "You had me excited for a moment.",
                "Your light shines no more.",
            )
        const val FAST_PHASE = 2
        const val GRAPPLE_PHASE = 2
        const val ENRAGE_PHASE = 5
        const val CRYSTAL_PHASES = 4

        const val SLAM_MAX = 44
        const val SLAM_IMPACT_TICKS = 3
        const val SLAM_LOCK_TICKS = 4
        private val SLAM_GRAPHICS =
            listOf(
                "spotanim.spotanim_colossi_finalboss_01_melee",
                "spotanim.spotanim_colossi_finalboss_02_melee",
                "spotanim.spotanim_colossi_finalboss_03_melee",
            )

        private const val PERCENT = 100
        const val GRAPPLE_WINDOW = 4
        const val GRAPPLE_MAX = 44

        private const val SAND_ROLLS = 5
        private const val SAND_RADIUS = 4
        private const val SAND_STRIKE_DELAY = 30
        private const val SAND_RUMBLE_DELAY = 90
        private const val SAND_BURN_TICKS = 3
        private const val SAND_LOC_TICKS = 5
        private const val LOC_ANGLES = 4
        private val SAND_DAMAGE = 6..8
        private const val SAND_LOC = "loc.colosseum_holy_fire"
        private const val SAND_CLEARED_LOC = "loc.invisible_type8_nonblocking"
        private const val SAND_DESPAWN_SEQ = "seq.vfx_colosseum_hot_sand_01_despawn_01"
        private const val SAND_STRIKE = "spotanim.vfx_colosseum_sunfire_lightning_01_beam_01"
        private const val SAND_STRIKE_SYNTH = "synth.colosseum_sol_sand_strike"
        private const val SAND_RUMBLE_SYNTH = "synth.colosseum_sol_sand_rumble"

        private const val CRYSTAL = "npc.colosseum_beam_crystal"
        private const val CRYSTAL_SPAWN_SEQ = "seq.npc_colosseum_crystal_spawn_01"
        private const val CRYSTAL_CHARGE_SEQ = "seq.npc_colosseum_crystal_charge_01"
        private const val CRYSTAL_ATTACK_SEQ = "seq.npc_colosseum_crystal_attack_01"
        private const val CRYSTAL_DESPAWN_SEQ = "seq.npc_colosseum_crystal_despawn_01"
        private val CHARGE_BEAMS =
            (1..4).map { "spotanim.vfx_colosseum_crystal_charge_01_beam_0$it" }
        private val ATTACK_BEAMS =
            (1..4).map { "spotanim.vfx_colosseum_crystal_attack_01_beam_0$it" }
        private const val BEAM_IMPACT = "spotanim.vfx_colosseum_crystal_attack_01_impact_01"
        private const val BEAM_IMPACT_SYNTH = "synth.colosseum_sol_beam_impact"
        private const val CRYSTAL_LOCK_SYNTH = "synth.colosseum_sol_crystal_lock"
        private const val CRYSTAL_CUE_SYNTH = "synth.colosseum_sol_crystal_cue"
        private const val CRYSTAL_LOCK_DELAY = 75
        private const val CRYSTAL_CUE_DELAY = 210
        private const val ENRAGE_CUE_DELAY = 180
        private const val BEAM_SOUND_RADIUS = 20
        private const val BEAM_HEIGHT = 128
        private const val BEAM_STEP_DELAY = 3
        private val BEAM_DAMAGE = 65..75
        private const val CRYSTAL_EDGES_ROW = "dbrow.colosseum_sol_crystal_edges"
        private const val CRYSTAL_SPAWN_SPREAD = 3
        private val CRYSTAL_FIRST_STEP = 3..4
        private val CRYSTAL_X_RANGE = 1819 to 1830
        private val CRYSTAL_Z_RANGE = 3101 to 3112
        private const val CRYSTAL_STEP_TICKS = 2
        private const val CUE_TO_CHARGE = 3
        private const val CRYSTAL_FIRE_DELAY = 4
        private const val ENRAGE_FIRE_DELAY = 3
        private const val CHARGE_TO_RESUME = 7
        private const val ENRAGE_CHARGE_TO_RESUME = 5
        private const val ENRAGE_VOLLEY_COOLDOWN = 11
        private const val ENRAGE_SAND_FIRST = 7
        private const val ENRAGE_SAND_INTERVAL = 3
        private val VOLLEY_COOLDOWN = 30..40

        private val WALLS = (1..6).map { "loc.shield_combatant_$it" }

        const val ARENA_MIN_X = 1818
        const val ARENA_MAX_X = 1831
        const val ARENA_MIN_Z = 3100
        const val ARENA_MAX_Z = 3113
    }
}
