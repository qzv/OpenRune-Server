package org.rsmod.content.bosses.yama

import dev.openrune.types.ObjectServerType
import dev.openrune.util.WeaponCategory
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.EnumMap
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.disableOverheadPrayers
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.overheadProtectionPrayerVarbits
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.setActiveMoveSpeed
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.type.getOrNull
import org.rsmod.map.CoordGrid

internal class GlyphState(
    val element: YamaElement,
    val origin: CoordGrid,
    val centre: CoordGrid,
    var loc: LocInfo? = null,
    var active: Boolean = false,
)

internal class YamaPlayerState {
    var lastTile: CoordGrid? = null
    var lastWaveHit = Int.MIN_VALUE / 2
    var noRunUntil = 0
    var speedBefore: MoveSpeed? = null
    var runInvertBefore: Int? = null
    var runInvertUntil = 0
    var lastSpecialEnergy = -1
    var prayerSince = -1
    var lastPrayer = -1
    var lastHitpoints = -1
    var breathless = false
    var deathLineSaid = false
    val immuneUntil = EnumMap<YamaElement, Int>(YamaElement::class.java)

    fun restoreRunInvert(player: Player) {
        val before = runInvertBefore ?: return
        runInvertBefore = null
        player.runInvertMode = before
    }
}

internal class YamaFight(val layout: GlyphLayout, var target: Player) {
    val players = arrayListOf(target)
    private val states = HashMap<Player, YamaPlayerState>()
    val glyphs = ArrayList<GlyphState>()
    var yama: Npc? = null
    var ticking = false
    var inJudge = false
    var dx = 0
    var dz = 0
    var phase = 1
    var attacks = 0
    var contract: YamaContract? = null
    var chosenPiece: String? = null
    var contractHolder: Player? = null
    var startTick = 0
    var throne: Npc? = null
    var finalStandards = 0
    var approachAt = -1
    var approached = false
    val flares = ArrayList<FlareState>()
    val scheduled = ArrayList<ScheduledHazard>()
    val streaks = ArrayList<Streak>()
    val waves = ArrayList<ActiveWave>()

    val creator: Player
        get() = players.first()

    fun state(player: Player): YamaPlayerState = states.getOrPut(player) { YamaPlayerState() }

    fun remove(player: Player) {
        players.remove(player)
        states.remove(player)?.restoreRunInvert(player)
    }

    fun removeAll() {
        players.toList().forEach(::remove)
    }

    fun template(coords: CoordGrid): CoordGrid =
        CoordGrid(coords.x - dx, coords.z - dz, coords.level)

    fun instance(x: Int, z: Int): CoordGrid = CoordGrid(x + dx, z + dz, target.coords.level)

    fun isImmune(player: Player, element: YamaElement, tick: Int): Boolean =
        (state(player).immuneUntil[element] ?: 0) > tick
}

@Singleton
internal class YamaFights
@Inject
constructor(
    private val deps: BossDeps,
    private val manager: InstanceManager,
    private val hazards: YamaHazards,
    private val voidFlares: YamaVoidFlares,
    private val attackTypes: AttackTypes,
    private val contractOverlay: YamaContractOverlay,
    private val aiPlayerInteractions: AiPlayerInteractions,
) {
    private val fights = HashMap<InstanceId, YamaFight>()

    fun of(player: Player): YamaFight? = manager.sessionForPlayer(player)?.let { fights[it.id] }

    fun create(
        session: InstanceSession,
        players: List<Player>,
        layout: GlyphLayout,
        contract: YamaContract? = null,
    ): YamaFight {
        end(session.id, closeOverlay = false)
        return YamaFight(layout, players[deps.random.of(players.size)]).also {
            it.players.clear()
            it.players += players
            it.contract = contract
            it.startTick = deps.mapClock.cycle
            fights[session.id] = it
        }
    }

    fun acquisitionCrash(player: Player) {
        val fight = of(player) ?: return
        if (fight.contract?.acquisition != true || fight.phase >= YamaPhases.FINAL) return
        hazards.crash(fight, deps.mapClock.cycle, 1, ACQUISITION_CRASH_DELAY, player)
    }

    fun byYama(npc: Npc): YamaFight? = fights.values.firstOrNull { it.yama === npc }

    fun finish(fight: YamaFight) {
        fight.ticking = false
        hazards.clear(fight)
        voidFlares.clear(fight, 0)
    }

    fun end(session: InstanceSession) = end(session.id)

    fun end(id: InstanceId, closeOverlay: Boolean = true) {
        val fight = fights.remove(id) ?: return
        fight.ticking = false
        if (closeOverlay) fight.players.forEach(contractOverlay::close)
        fight.removeAll()
        for (glyph in fight.glyphs) glyph.loc?.let { deps.locRepo.del(it, 0) }
    }

    fun leave(id: InstanceId, player: Player) {
        val fight = fights[id] ?: return
        if (player !in fight.players) return
        fight.remove(player)
        contractOverlay.close(player)
        if (fight.players.isEmpty()) {
            end(id)
            return
        }
        if (fight.target === player) retarget(fight, fight.players.first())
    }

    fun retarget(fight: YamaFight, next: Player) {
        val yama = fight.yama
        if (fight.target !== next) {
            fight.target = next
            YamaLines.pick(YamaLineGroup.AGGRO, deps.random)?.let { yama?.say(it) }
        }
        yama?.apPlayer2(next, aiPlayerInteractions)
    }

    fun placeGlyphs(session: InstanceSession, fight: YamaFight) {
        fight.glyphs.clear()
        val spawn = YamaPhases.SPAWN
        for (slot in YamaGlyphs.SLOTS) {
            val template = CoordGrid(spawn.x + slot.dx, spawn.z + slot.dz)
            val origin = manager.resolveCoord(session, template) ?: continue
            val centreTemplate = CoordGrid(spawn.x + slot.centreDx, spawn.z + slot.centreDz)
            val centre = manager.resolveCoord(session, centreTemplate) ?: continue
            val glyph = GlyphState(fight.layout.elementAt(slot), origin, centre)
            setGlyph(glyph, active = true)
            fight.glyphs += glyph
        }
    }

    fun deactivateAll(fight: YamaFight) {
        for (glyph in fight.glyphs) if (glyph.active) setGlyph(glyph, active = false)
    }

    fun attackStarted(player: Player): Int {
        val fight = of(player) ?: return 0
        val index = fight.attacks++
        if (index == 0) hazards.phaseStarted(fight, deps.mapClock.cycle)
        return index
    }

    fun endPhase(fight: YamaFight) {
        hazards.phaseEnded(fight)
        voidFlares.clear(fight, FLARE_PORTAL_DELAY)
        fight.finalStandards = 0
        for (player in fight.players) {
            player.burnTotal = 0
            player.burnSize = 0
        }
        fight.attacks = 0
    }

    fun startTicking(session: InstanceSession, fight: YamaFight, yama: Npc) {
        val spawn = YamaPhases.SPAWN
        val origin = manager.resolveCoord(session, spawn)
        if (origin != null) {
            fight.dx = origin.x - spawn.x
            fight.dz = origin.z - spawn.z
        }
        fight.yama = yama
        fight.ticking = true
        deps.worldQueues.add(1) { tick(session, fight) }
    }

    fun disableRunInvert(fight: YamaFight, player: Player, ticks: Int) {
        val state = fight.state(player)
        state.runInvertUntil = maxOf(state.runInvertUntil, deps.mapClock.cycle + ticks)
        if (state.runInvertBefore == null) {
            state.runInvertBefore = player.runInvertMode
            player.runInvertMode = 0
        }
        deps.worldQueues.add(ticks) {
            if (deps.mapClock.cycle >= state.runInvertUntil) state.restoreRunInvert(player)
        }
    }

    private fun tick(session: InstanceSession, fight: YamaFight) {
        fight.players.filter { manager.sessionForPlayer(it) != session }.forEach(fight::remove)
        val alive = fight.yama?.let { it.hitpoints > 0 } == true
        if (!fight.ticking || !alive || fight.players.isEmpty()) {
            fight.ticking = false
            return
        }
        if (fight.target !in fight.players) retarget(fight, fight.players.first())
        val now = deps.mapClock.cycle
        val yama = fight.yama
        if (yama != null) {
            sayDeathLine(fight, yama)
            scaleAtThreshold(fight, yama)
        }
        hazards.tick(fight, now)
        voidFlares.tick(fight, now)
        pursue(fight, now)
        for (player in fight.players.toList()) {
            enforceWalking(fight, player, now)
            applyContractRules(fight, player, now)
            limitRestores(fight, player)
            val glyph = fight.glyphs.firstOrNull { it.active && it.centre == player.coords }
            if (glyph != null) absorb(fight, player, glyph, now)
        }
        deps.worldQueues.add(1) { tick(session, fight) }
    }

    private fun sayDeathLine(fight: YamaFight, yama: Npc) {
        for (player in fight.players) {
            val state = fight.state(player)
            val dead = player.hitpoints <= 0
            if (dead && !state.deathLineSaid) yama.say(DEATH_LINE)
            state.deathLineSaid = dead
        }
    }

    private fun scaleAtThreshold(fight: YamaFight, yama: Npc) {
        if (fight.inJudge) return
        val threshold =
            YamaSpecs.transitionThreshold(fight.phase, fight.contract != null) ?: return
        val fraction = yama.hitpoints.toDouble() / yama.baseHitpointsLvl.coerceAtLeast(1)
        if (fraction <= threshold) {
            deps.encounter(yama).damageScale = YamaPhases.TRANSITION_DAMAGE_SCALE
        }
    }

    private fun approach(fight: YamaFight, yama: Npc, now: Int): Boolean {
        if (fight.approachAt < 0) fight.approachAt = now + APPROACH_DELAY
        val distance = YamaPhases.footprintDistance(yama, fight.target.coords)
        val arrived = now >= fight.approachAt && distance <= APPROACH_RANGE
        if (arrived || fight.attacks > 0 || now >= fight.approachAt + APPROACH_TICKS) {
            fight.approached = true
            return false
        }
        yama.apRangeOverride = APPROACH_RANGE
        yama.movementLocked = now < fight.approachAt
        return true
    }

    private fun pursue(fight: YamaFight, now: Int) {
        val yama = fight.yama ?: return
        val finalPhase = fight.phase >= YamaPhases.FINAL
        val meleeWeapon = wieldsMeleeWeapon(fight.target)
        yama.defenceBonusOverrides.clear()
        yama.defenceBonusOverrides += YamaDefences.of(finalPhase, meleeWeapon)
        if (fight.inJudge || finalPhase) {
            yama.apRangeOverride = null
            yama.movementLocked = true
            return
        }
        val melee = fight.contract?.pursues == true || meleeWeapon
        val approaching = !melee && fight.phase == APPROACH_PHASE && !fight.approached
        if (approaching && approach(fight, yama, now)) return
        yama.apRangeOverride = if (melee) MELEE_RANGE else null
        yama.movementLocked = !melee
    }

    private fun wieldsMeleeWeapon(player: Player): Boolean {
        val weapon = getOrNull(player.righthand)
        val category = WeaponCategory.getOrUnarmed(weapon?.weaponCategory?.id)
        if (category == WeaponCategory.Staff || category == WeaponCategory.BladedStaff) return false
        return (0 until STANCES).all { attackTypes.resolve(weapon, it)?.isMelee != false }
    }

    private fun applyContractRules(fight: YamaFight, player: Player, now: Int) {
        val contract = fight.contract ?: return
        val state = fight.state(player)
        if (contract.doublesSpecialRegen) {
            val gain = player.specialEnergy - state.lastSpecialEnergy
            if (state.lastSpecialEnergy >= 0 && gain > 0) {
                player.specialEnergy = minOf(MAX_SPECIAL_ENERGY, player.specialEnergy + gain)
            }
            state.lastSpecialEnergy = player.specialEnergy
        }
        if (contract.forcesPrayerOff) forcePrayerOff(player, state, now)
        if (contract.drainsRunEnergy) drainRunEnergy(fight, player, state, now)
    }

    private fun forcePrayerOff(player: Player, state: YamaPlayerState, now: Int) {
        val active = overheadProtectionPrayerVarbits.any { player.vars[it] == 1 }
        if (!active) {
            state.prayerSince = -1
            return
        }
        if (state.prayerSince < 0) state.prayerSince = now
        if (now - state.prayerSince >= PRAYER_FORCED_OFF_TICKS) {
            player.disableOverheadPrayers()
            state.prayerSince = -1
        }
    }

    private fun drainRunEnergy(fight: YamaFight, player: Player, state: YamaPlayerState, now: Int) {
        val damage = maxOf(0, state.lastHitpoints - player.hitpoints)
        val drain = YamaRunDrain.amount(now - fight.startTick, damage)
        player.runEnergy = maxOf(0, player.runEnergy - drain)
        if (player.runEnergy > 0 || state.breathless) return
        state.breathless = true
        player.queueHit(1, HitType.Typeless, player.hitpoints, deps.playerHitModifier)
    }

    private fun limitRestores(fight: YamaFight, player: Player) {
        val contract = fight.contract ?: return
        val state = fight.state(player)
        val prayerGain = player.prayerLvl - state.lastPrayer
        if (state.lastPrayer >= 0 && prayerGain > RESTORE_THRESHOLD) {
            if (contract.blocksPrayerRestore) {
                player.statSub(PRAYER, prayerGain, 0)
            } else if (contract.halvesRestores) {
                player.statSub(PRAYER, prayerGain - prayerGain / 2, 0)
            }
        }
        val healthGain = player.hitpoints - state.lastHitpoints
        if (state.lastHitpoints >= 0 && healthGain > RESTORE_THRESHOLD) {
            if (contract.blocksHealing) {
                player.statSub(HITPOINTS, healthGain, 0)
            } else if (contract.halvesRestores) {
                player.statSub(HITPOINTS, healthGain - healthGain / 2, 0)
            }
        }
        state.lastPrayer = player.prayerLvl
        state.lastHitpoints = player.hitpoints
    }

    private fun enforceWalking(fight: YamaFight, player: Player, now: Int) {
        val state = fight.state(player)
        val before = state.speedBefore ?: return
        if (now < state.noRunUntil) {
            player.setActiveMoveSpeed(MoveSpeed.Walk)
            return
        }
        player.setActiveMoveSpeed(before)
        state.speedBefore = null
    }

    private fun absorb(fight: YamaFight, player: Player, glyph: GlyphState, now: Int) {
        val element = glyph.element
        val glyphTicks = fight.contract?.glyphTicks ?: GLYPH_IMMUNITY_TICKS
        fight.state(player).immuneUntil[element] = now + glyphTicks
        player.mes(element.colour(YamaText.ABSORB_PREFIX) + element.glyphName + ".", ChatType.Spam)
        player.spotanim(element.immunitySpotanim, slot = IMMUNITY_SLOT)
        setGlyph(glyph, active = false)
        val costs = YamaGlyphCosts.range(element, yamaDistance(fight, glyph))
        val scale = fight.contract?.glyphCostScale ?: 1
        val cost = deps.random.of(costs.first, costs.last) * scale
        if (element == YamaElement.FIRE) {
            player.queueHit(1, HitType.Typeless, cost, deps.playerHitModifier)
        } else {
            player.statSub(PRAYER, cost, 0)
        }
        deps.worldQueues.add(glyphTicks) {
            if (!fight.isImmune(player, element, deps.mapClock.cycle)) {
                val fade = YamaText.FADE_COLOUR + "The " + element.glyphName + " fades away."
                player.mes(fade, ChatType.Spam)
            }
        }
    }

    private fun yamaDistance(fight: YamaFight, glyph: GlyphState): Int {
        val yama = fight.yama ?: return 0
        return YamaPhases.footprintDistance(yama, glyph.centre)
    }

    private fun setGlyph(glyph: GlyphState, active: Boolean) {
        glyph.loc?.let { deps.locRepo.del(it, 0) }
        glyph.active = active
        if (active) {
            glyph.loc = addLoc(glyph, glyph.element.glyphLoc)
            return
        }
        glyph.loc = addLoc(glyph, glyph.element.deactivateLoc)
        deps.worldQueues.add(1) {
            glyph.loc?.let { deps.locRepo.del(it, 0) }
            glyph.loc =
                deps.locRepo.add(
                    glyph.origin,
                    INACTIVE_GLYPH_LOC,
                    Int.MAX_VALUE,
                    LocAngle[GLYPH_ROTATION],
                    LocShape.CentrepieceStraight,
                )
        }
    }

    private fun addLoc(glyph: GlyphState, type: ObjectServerType): LocInfo =
        deps.locRepo.add(
            glyph.origin,
            type,
            Int.MAX_VALUE,
            LocAngle[GLYPH_ROTATION],
            LocShape.CentrepieceStraight,
        )

    private companion object {
        private const val GLYPH_IMMUNITY_TICKS = 10
        private const val FLARE_PORTAL_DELAY = 3
        private const val RESTORE_THRESHOLD = 2
        private const val MELEE_RANGE = 1
        private const val STANCES = 4
        private const val MAX_SPECIAL_ENERGY = 1000
        private const val PRAYER_FORCED_OFF_TICKS = 2
        private const val ACQUISITION_CRASH_DELAY = 4
        private const val IMMUNITY_SLOT = 2
        private const val GLYPH_ROTATION = 2
        private const val INACTIVE_GLYPH_LOC = "loc.floorkit_summoning03_inactive"
        private const val DEATH_LINE = "A stroke of bad luck. You could have done better."
        private const val APPROACH_PHASE = 2
        private const val APPROACH_DELAY = 6
        private const val APPROACH_RANGE = 4
        private const val APPROACH_TICKS = 4
        private const val PRAYER = "stat.prayer"
        private const val HITPOINTS = "stat.hitpoints"
    }
}
