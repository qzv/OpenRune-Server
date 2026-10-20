package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.bosses.runtime.suppressAttacks
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.refs.params
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.disableOverheadPrayers
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.CamShakeAxis
import org.rsmod.api.player.output.Camera
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.overheadProtectionPrayerVarbits
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

internal class Surge(val npc: Npc, var tile: CoordGrid, val spawnTick: Int)

internal class JudgePending(val tick: Int, val action: () -> Unit)

internal class JudgeGroup(val session: InstanceSession, val yama: Npc, val nextPhase: String) {
    val fights = ArrayList<JudgeFight>()
    var finished = false
    val intoFinalPhase: Boolean
        get() = nextPhase == YamaPhases.FINAL_NAME
}

internal class JudgeFight(
    val group: JudgeGroup,
    val player: Player,
    val judge: Npc,
    val dx: Int,
    val dz: Int,
) {
    val surges = ArrayList<Surge>()
    val pending = ArrayList<JudgePending>()
    var nextCast = 0
    var casts = 0
    var endTick = Int.MAX_VALUE
    var protectsMagic = false
    var lastHitTick = -1
    var prayerAtAttack: Boolean? = null

    fun template(coords: CoordGrid): CoordGrid =
        CoordGrid(coords.x - dx, coords.z - dz, coords.level)

    fun instance(template: CoordGrid): CoordGrid =
        CoordGrid(template.x + dx, template.z + dz, template.level)
}

@Singleton
internal class YamaJudge
@Inject
constructor(
    private val deps: BossDeps,
    private val manager: InstanceManager,
    private val fights: YamaFights,
    private val launcher: ProtectedAccessLauncher,
    private val bossHpBar: BossHpBarScript,
    private val contractOverlay: YamaContractOverlay,
) {
    private val active = HashMap<Npc, JudgeFight>()

    fun registerHandlers(ctx: ScriptContext) {
        with(ctx) {
            val type = ServerCacheManager.getNpc(JUDGE_NPC.asRSCM(RSCMType.NPC)) ?: return
            onNpcQueue(type, "queue.death") { finish(npc) }
            onModifyNpcHit(type) { applyPrayers(this) }
            onApLoc1(STONE_LOC) { hop(it.loc) }
        }
    }

    private fun applyPrayers(event: NpcHitEvents.Modify) {
        val fight = active[event.npc] ?: return
        val hit = event.hit
        if (!hit.isFromPlayer) return
        val now = deps.mapClock.cycle
        if (fight.lastHitTick == now) {
            hit.damage = 0
            return
        }
        fight.lastHitTick = now
        val attacker = hit.sourceSlot?.let { deps.playerList[it] }
        val maxHit = attacker?.vars?.get(PLAYER_MAX_HIT) ?: hit.damage
        val prayerAtAttack = fight.prayerAtAttack
        fight.prayerAtAttack = null
        val protectsMagic = prayerAtAttack ?: fight.protectsMagic
        val outcome = YamaJudgePrayers.resolve(protectsMagic, hit.type, maxHit)
        hit.damage = outcome.damage
        val lethal = outcome.damage >= event.npc.hitpoints
        hit.sourceMaxDamageThreshold = if (lethal) Int.MAX_VALUE else outcome.damage
        shareDamage(fight, outcome.damage)
        if (prayerAtAttack != null || outcome.protectsMagic == fight.protectsMagic) return
        fight.protectsMagic = outcome.protectsMagic
        showPrayers(fight)
    }

    fun onPlayerAttack(npc: Npc, attack: CombatAttack.PlayerAttack) {
        val fight = active[npc] ?: return
        fight.prayerAtAttack = fight.protectsMagic
        if (attack is CombatAttack.Ranged) return
        fight.protectsMagic = attack is CombatAttack.Magic
        showPrayers(fight)
    }

    private fun shareDamage(fight: JudgeFight, damage: Int) {
        for (other in fight.group.fights) {
            if (other === fight || !active.containsKey(other.judge)) continue
            val judge = other.judge
            judge.hitpoints = maxOf(0, judge.hitpoints - damage)
            val bar = judge.visHeadbar(params.headbar)
            val base = judge.baseHitpointsLvl
            val fill = (judge.hitpoints * bar.segments + base - 1) / base
            judge.showHeadbar(Headbar.fromNoSource(bar.id, bar.id, fill, fill, 0, 0))
        }
    }

    private fun showPrayers(fight: JudgeFight) {
        val index = if (fight.protectsMagic) PRAYER_MAGIC_INDEX else PRAYER_MELEE_INDEX
        fight.judge.setHeadIcon(0, PRAYER_HEADICON_GRAPHIC, index)
    }

    private fun ProtectedAccess.hop(stone: BoundLocInfo) {
        if (!isWithinApRange(stone, distance = STONE_AP_RANGE)) return
        val from = coords
        val to = stone.coords
        val radians = atan2((to.x - from.x).toDouble(), (to.z - from.z).toDouble())
        val turn = (radians * ANGLE_UNITS / (2 * PI)).roundToInt()
        val facing = Math.floorMod(FACE_NORTH + turn, ANGLE_UNITS)
        exactMove(from, to, delay1 = 0, delay2 = HOP_CYCLES, dir = facing)
        anim(HOP_SEQ)
    }

    fun begin(yama: Npc, target: Player, nextPhase: String) {
        val session = manager.sessionForPlayer(target) ?: return
        val fight = fights.of(target) ?: return
        fights.endPhase(fight)
        fight.inJudge = true
        yama.resetFaceEntity()
        fight.players.forEach { it.clearInteraction() }
        deps.encounter(yama).damageScale = YamaPhases.TRANSITION_DAMAGE_SCALE
        YamaLines.pick(YamaLineGroup.TRANSITION, deps.random)?.let(yama::say)
        yama.anim(PORTAL_SEQ)
        yama.spotanim(PORTAL_SPOTANIM)
        val group = JudgeGroup(session, yama, nextPhase)
        fight.players.forEachIndexed { index, player ->
            transport(group, player, index * PARTNER_POCKET_OFFSET)
        }
    }

    private fun transport(group: JudgeGroup, player: Player, offset: Int) {
        val fight = fights.of(player) ?: return
        player.spotanim(PLAYER_PORTAL_SPOTANIM, slot = PORTAL_SLOT)
        launcher.launch(player) {
            delay(PORTAL_FALL_DELAY)
            player.anim(PORTAL_FALL_SEQ, delay = PORTAL_FALL_CLIENT_DELAY)
            fights.deactivateAll(fight)
            delay(HUD_FADE_DELAY - PORTAL_FALL_DELAY)
            bossHpBar.onClose(player, group.yama, instant = true)
            fadeOverlay(FADE_FROM, FADE_TRANSPARENT, FADE_TO, FADE_OPAQUE, FADE_CYCLES)
            minimapHideMap()
            delay(TELEPORT_DELAY - HUD_FADE_DELAY)
            val pocket = CoordGrid(JUDGE_ARRIVAL.x + offset, JUDGE_ARRIVAL.z)
            val arrival = manager.resolveCoord(group.session, pocket) ?: return@launch
            telejump(arrival, TeleportType.Exempt)
            faceDirection(Direction.North)
            player.anim(FALL_ON_FACE_SEQ)
            arrivalSpotanim(player)
            player.transitionPhase = 1
            player.transitionDamageTaken = 0
            spawnJudge(group, player, arrival)
            minimapReset()
            fadeOverlay(FADE_FROM, FADE_OPAQUE, FADE_TO, FADE_TRANSPARENT, FADE_CYCLES)
            delay(OVERLAY_CLOSE_DELAY)
            closeFadeOverlay()
            disableProtection(player)
            delay(ENTRY_OVERLAY_DELAY - OVERLAY_CLOSE_DELAY)
            contractOverlay.open(player, fights.of(player)?.contract)
        }
    }

    private fun spawnJudge(group: JudgeGroup, player: Player, arrival: CoordGrid) {
        if (group.finished) return
        val dx = arrival.x - JUDGE_ARRIVAL.x
        val dz = arrival.z - JUDGE_ARRIVAL.z
        val type = ServerCacheManager.getNpc(JUDGE_NPC.asRSCM(RSCMType.NPC)) ?: return
        val judge = Npc(type, CoordGrid(JUDGE_SPAWN.x + dx, JUDGE_SPAWN.z + dz, arrival.level))
        deps.npcRepo.add(judge, Int.MAX_VALUE)
        manager.registerSessionNpc(player, judge)
        judge.baseHitpointsLvl = JUDGE_HP
        val partner = group.fights.firstOrNull { active.containsKey(it.judge) }?.judge
        judge.hitpoints = partner?.hitpoints ?: JUDGE_HP
        judge.vars[GUARANTEED_HIT] = 1
        val fight = JudgeFight(group, player, judge, dx, dz)
        group.fights += fight
        active[judge] = fight
        showPrayers(fight)
        fight.nextCast = deps.mapClock.cycle + FIRST_CAST_DELAY
        deps.worldQueues.add(1) {
            if (active.containsKey(judge)) judge.facePlayer(player)
            tick(fight)
        }
    }

    private fun disableProtection(player: Player) {
        if (overheadProtectionPrayerVarbits.none { player.vars[it] == 1 }) return
        player.disableOverheadPrayers()
        player.mes(YamaText.INJURED)
    }

    private fun tick(fight: JudgeFight) {
        val now = deps.mapClock.cycle
        if (manager.sessionForPlayer(fight.player) != fight.group.session) {
            abandon(fight)
            return
        }
        if (now >= fight.endTick) {
            clear(fight)
            return
        }
        processSurges(fight, now)
        val due = fight.pending.filter { it.tick <= now }
        fight.pending.removeAll(due.toSet())
        due.forEach { it.action() }
        if (active.containsKey(fight.judge) && now >= fight.nextCast) cast(fight, now)
        deps.worldQueues.add(1) { tick(fight) }
    }

    private fun processSurges(fight: JudgeFight, now: Int) {
        val playerTile = fight.template(fight.player.coords)
        val iterator = fight.surges.iterator()
        while (iterator.hasNext()) {
            val surge = iterator.next()
            if (surge.tile.x == playerTile.x && surge.tile.z == playerTile.z) {
                hurt(fight, deps.random.of(SURGE_MIN_HIT, SURGE_MAX_HIT))
            }
            if (surge.tile.z <= SURGE_END_Z) {
                if (surge.npc.isSlotAssigned) deps.npcRepo.del(surge.npc, Int.MAX_VALUE)
                iterator.remove()
            } else if (now - surge.spawnTick >= SURGE_DWELL) {
                val next = CoordGrid(surge.tile.x, surge.tile.z - 1, surge.tile.level)
                val from = fight.instance(surge.tile)
                val to = fight.instance(next)
                val glide = SURGE_GLIDE_CYCLES
                PathingEntityCommon.exactMove(surge.npc, from, to, 0, glide, 0, deps.collision)
                surge.tile = next
            }
        }
    }

    private fun cast(fight: JudgeFight, now: Int) {
        val target = fight.template(fight.player.coords)
        fight.judge.anim(CAST_SEQ)
        fight.casts++
        val contract = fights.of(fight.player)?.contract != null
        if (target.z < FIREBALL_Z) {
            spawnSurges(fight, now)
            fight.nextCast = now + if (contract) CONTRACT_CAST_GAP else SURGE_CAST_GAP
        } else {
            launchFireball(fight, now, target)
            if (contract) launchFireball(fight, now, scatter(target))
            fight.nextCast = now + if (contract) CONTRACT_CAST_GAP else FIREBALL_CAST_GAP
        }
        if (fight.casts in SHAKE_CASTS) {
            shake(fight.player)
            fight.pending +=
                JudgePending(now + SHAKE_DELAY) {
                    hurt(fight, deps.random.of(SHAKE_MIN_HIT, SHAKE_MAX_HIT))
                }
        }
    }

    private fun scatter(target: CoordGrid): CoordGrid =
        CoordGrid(
            target.x + deps.random.of(-FIREBALL_SCATTER, FIREBALL_SCATTER),
            target.z + deps.random.of(-FIREBALL_SCATTER, FIREBALL_SCATTER),
            target.level,
        )

    private fun shake(player: Player) {
        for (axis in SHAKE_AXES) {
            Camera.camShake(player, axis, deps.random.of(SHAKE_MIN_RANDOM, SHAKE_MAX_RANDOM), 0, 0)
        }
        deps.worldQueues.add(SHAKE_RESET_DELAY) { Camera.camReset(player) }
    }

    private fun spawnSurges(fight: JudgeFight, now: Int) {
        val type = ServerCacheManager.getNpc(SURGE_NPC.asRSCM(RSCMType.NPC)) ?: return
        val count = if (deps.random.of(SURGE_PAIR_ROLL) < SURGE_PAIR_WEIGHT) 2 else 1
        val columns = SURGE_COLUMNS.toMutableList()
        repeat(count) {
            val x = columns.removeAt(deps.random.of(columns.size))
            val tile = CoordGrid(x, SURGE_SPAWN_Z, fight.judge.coords.level)
            val npc = Npc(type, fight.instance(tile))
            deps.npcRepo.add(npc, Int.MAX_VALUE)
            fight.surges += Surge(npc, tile, now)
        }
    }

    private fun launchFireball(fight: JudgeFight, now: Int, target: CoordGrid) {
        val source = fight.instance(CoordGrid(FIREBALL_SOURCE.x, FIREBALL_SOURCE.z, target.level))
        val landing = fight.instance(target)
        val type = ServerCacheManager.getProjectile(FIREBALL_PROJANIM.asRSCM(RSCMType.PROJANIM))
        checkNotNull(type) { "Projectile not found: $FIREBALL_PROJANIM" }
        deps.bossProjectile(
            FIREBALL_PROJECTILE.asRSCM(RSCMType.SPOTANIM),
            source,
            landing,
            type.startHeight,
            type.endHeight,
            type.delay,
            type.lengthAdjustment,
            type.angle,
            progress = type.progress,
        )
        deps.worldRepo.spotanimMap(
            SpotanimType(FIREBALL_SHADOW.asRSCM(RSCMType.SPOTANIM)),
            landing,
            delay = FIREBALL_SHADOW_DELAY,
        )
        deps.worldRepo.spotanimMap(
            SpotanimType(FIREBALL_IMPACT.asRSCM(RSCMType.SPOTANIM)),
            landing,
            height = FIREBALL_IMPACT_HEIGHT,
            delay = type.delay + type.lengthAdjustment,
        )
        fight.pending +=
            JudgePending(now + FIREBALL_LANDING) {
                val tile = fight.template(fight.player.coords)
                if (tile.x == target.x && tile.z == target.z) {
                    hurt(fight, deps.random.of(FIREBALL_MIN_HIT, FIREBALL_MAX_HIT))
                }
            }
    }

    private fun hurt(fight: JudgeFight, damage: Int) {
        fight.player.transitionDamageTaken = 1
        fight.player.queueHit(fight.judge, 1, HitType.Typeless, damage, deps.playerHitModifier)
    }

    private fun clear(fight: JudgeFight) {
        for (surge in fight.surges) {
            if (surge.npc.isSlotAssigned) deps.npcRepo.del(surge.npc, Int.MAX_VALUE)
        }
        fight.surges.clear()
        fight.pending.clear()
    }

    private fun abandon(fight: JudgeFight) {
        clear(fight)
        val removed = active.remove(fight.judge) != null
        if (removed && fight.judge.isSlotAssigned) deps.npcRepo.del(fight.judge, Int.MAX_VALUE)
        fight.group.fights.remove(fight)
    }

    private fun finish(judge: Npc) {
        val group = active[judge]?.group ?: return
        if (group.finished) return
        group.finished = true
        val now = deps.mapClock.cycle
        for (fight in group.fights) {
            active.remove(fight.judge)
            fight.endTick = now + SURGE_CLEAR_DELAY
            fight.judge.anim(DEATH_SEQ)
            fight.judge.clearHeadIcon(0)
            fight.judge.resetFaceEntity()
            deps.worldQueues.add(DEATH_ANIM_TICKS) {
                if (fight.judge.isSlotAssigned) deps.npcRepo.del(fight.judge, Int.MAX_VALUE)
            }
        }
        val yama = group.yama
        val players = group.fights.map { it.player }
        deps.worldQueues.add(RESET_DELAY) {
            players.forEach {
                it.transitionPhase = 0
                it.transitionDamageTaken = 0
            }
        }
        val homeDelay = if (group.intoFinalPhase) FINAL_YAMA_RETURN_DELAY else RESET_DELAY
        deps.worldQueues.add(homeDelay) {
            val state = players.firstNotNullOfOrNull { fights.of(it) } ?: return@add
            state.inJudge = false
            val home = manager.resolveCoord(group.session, YamaPhases.SPAWN)
            home?.let { yama.telejump(deps.collision, it) }
        }
        players.firstNotNullOfOrNull { fights.of(it) }?.let { it.phase++ }
        deps.encounter(yama).damageScale = 1.0
        deps.encounter(yama).transitionTo(group.nextPhase, now)
        deps.suppressAttacks(yama, RESUME_ATTACK_DELAY)
        deps.worldQueues.add(HP_BAR_RESTORE_DELAY) {
            val state = players.firstNotNullOfOrNull { fights.of(it) } ?: return@add
            state.players.forEach { bossHpBar.onOpen(it, yama) }
            fights.retarget(state, state.target)
        }
        if (!group.intoFinalPhase) {
            deps.worldQueues.add(GLYPH_RESTORE_DELAY) {
                val state = players.firstNotNullOfOrNull { fights.of(it) } ?: return@add
                fights.placeGlyphs(group.session, state)
            }
        }
        for (player in players) {
            player.spotanim(
                PLAYER_PORTAL_SPOTANIM,
                delay = RETURN_PORTAL_SPOTANIM_DELAY,
                slot = RETURN_PORTAL_SLOT,
            )
            deps.worldQueues.add(RETURN_FALL_DELAY) { returnToArena(group, player) }
        }
    }

    private fun returnToArena(group: JudgeGroup, player: Player) {
        if (manager.sessionForPlayer(player) != group.session) return
        val launched =
            launcher.launch(player) {
                player.anim(PORTAL_FALL_SEQ, delay = PORTAL_FALL_CLIENT_DELAY)
                fadeOverlay(FADE_FROM, FADE_TRANSPARENT, FADE_TO, FADE_OPAQUE, FADE_CYCLES)
                delay(RETURN_MINIMAP_DELAY - RETURN_FALL_DELAY)
                minimapHideMap()
                delay(RETURN_TELEPORT_DELAY - RETURN_MINIMAP_DELAY)
                val arrival = ARENA_RETURN_TILES[deps.random.of(ARENA_RETURN_TILES.size)]
                val coords = manager.resolveCoord(group.session, arrival) ?: return@launch
                telejump(coords, TeleportType.Exempt)
                faceDirection(Direction.South)
                player.anim(FALL_ON_FACE_SEQ)
                arrivalSpotanim(player)
                minimapReset()
                fadeOverlay(FADE_FROM, FADE_OPAQUE, FADE_TO, FADE_TRANSPARENT, FADE_CYCLES)
                delay(OVERLAY_CLOSE_DELAY)
                closeFadeOverlay()
                delay(RETURN_OVERLAY_DELAY - RETURN_TELEPORT_DELAY - OVERLAY_CLOSE_DELAY)
                contractOverlay.open(player, fights.of(player)?.contract)
            }
        if (!launched) deps.worldQueues.add(1) { returnToArena(group, player) }
    }

    private fun arrivalSpotanim(player: Player) {
        player.spotanim(JUDGE_ARRIVAL_SPOTANIM, height = JUDGE_ARRIVAL_HEIGHT, slot = PORTAL_SLOT)
    }

    private companion object {
        private const val JUDGE_NPC = "npc.yama_judge_of_yama"
        private const val JUDGE_HP = 400
        private const val GUARANTEED_HIT = "varn.guaranteed_hit"
        private const val PLAYER_MAX_HIT = "varp.com_maxhit"
        private const val PARTNER_POCKET_OFFSET = 50
        private const val PRAYER_HEADICON_GRAPHIC = 440
        private const val PRAYER_MELEE_INDEX = 7
        private const val PRAYER_MAGIC_INDEX = 6
        private const val STONE_LOC = "loc.yama_steppingstone"
        private const val SURGE_NPC = "npc.fire_surge_raised"
        private const val FIREBALL_PROJECTILE = "spotanim.luc2_undead_demon_fireball_large_proj"
        private const val FIREBALL_PROJANIM = "projanim.yama_judge_fireball"

        private const val PORTAL_SEQ = "seq.npc_yama01_portal01"
        private const val PORTAL_SPOTANIM = "spotanim.vfx_yama_portal_shadow_spotanim01"
        private const val PLAYER_PORTAL_SPOTANIM = "spotanim.vfx_yama_floor_portal_spotanim01"
        private const val JUDGE_ARRIVAL_SPOTANIM = "spotanim.vfx_yama_floor_portal_spotanim02"
        private const val PORTAL_FALL_SEQ = "seq.human_yama_portalfall"
        private const val FALL_ON_FACE_SEQ = "seq.human_yama_portal_fall_on_face"
        private const val HOP_SEQ = "seq.human_yama_shortjump01"
        private const val CAST_SEQ = "seq.demon_casting"
        private const val DEATH_SEQ = "seq.judgeofyama_death"

        private const val PORTAL_SLOT = 5
        private const val PORTAL_FALL_CLIENT_DELAY = 5
        private const val JUDGE_ARRIVAL_HEIGHT = 638
        private const val HOP_CYCLES = 27
        private const val STONE_AP_RANGE = 3
        private const val FACE_NORTH = 1024

        private const val PORTAL_FALL_DELAY = 2
        private const val HUD_FADE_DELAY = 4
        private const val TELEPORT_DELAY = 6
        private const val OVERLAY_CLOSE_DELAY = 2
        private const val FIRST_CAST_DELAY = 1
        private const val ENTRY_OVERLAY_DELAY = 6
        private const val RESET_DELAY = 5
        private const val FINAL_YAMA_RETURN_DELAY = 6
        private const val RETURN_PORTAL_SPOTANIM_DELAY = 90
        private const val RETURN_PORTAL_SLOT = 2
        private const val RETURN_MINIMAP_DELAY = 8
        private const val RETURN_OVERLAY_DELAY = 14
        private const val HP_BAR_RESTORE_DELAY = 11
        private const val GLYPH_RESTORE_DELAY = 12
        private const val DEATH_ANIM_TICKS = 5
        private const val RETURN_FALL_DELAY = 6
        private const val RETURN_TELEPORT_DELAY = 9
        private const val RESUME_ATTACK_DELAY = 15

        private const val CONTRACT_CAST_GAP = 3
        private const val SURGE_SPAWN_Z = 10097
        private const val SURGE_END_Z = 10070
        private const val SURGE_DWELL = 2
        private const val SURGE_GLIDE_CYCLES = 30
        private const val SURGE_CLEAR_DELAY = 5
        private const val SURGE_CAST_GAP = 4
        private const val SURGE_PAIR_ROLL = 258
        private const val SURGE_PAIR_WEIGHT = 210
        private const val SURGE_MIN_HIT = 22
        private const val SURGE_MAX_HIT = 25
        private const val FIREBALL_CAST_GAP = 5
        private const val FIREBALL_Z = 10094
        private const val FIREBALL_LANDING = 4
        private const val FIREBALL_SCATTER = 2
        private const val FIREBALL_SHADOW = "spotanim.gargboss_debris_shadow_90"
        private const val FIREBALL_SHADOW_DELAY = 30
        private const val FIREBALL_IMPACT = "spotanim.luc2_undead_demon_fireball_large_impact"
        private const val FIREBALL_IMPACT_HEIGHT = 30
        private const val FIREBALL_MIN_HIT = 9
        private const val FIREBALL_MAX_HIT = 12
        private val SHAKE_CASTS = setOf(5, 10)
        private const val ANGLE_UNITS = 2048
        private const val SHAKE_DELAY = 2
        private const val SHAKE_MIN_HIT = 5
        private const val SHAKE_MAX_HIT = 6
        private const val SHAKE_MIN_RANDOM = 5
        private const val SHAKE_MAX_RANDOM = 8
        private const val SHAKE_RESET_DELAY = 1
        private val SHAKE_AXES =
            listOf(CamShakeAxis.LEFT_RIGHT, CamShakeAxis.UP_DOWN, CamShakeAxis.FORWARDS_BACKWARDS)

        private const val FADE_FROM = 0
        private const val FADE_TO = 0
        private const val FADE_TRANSPARENT = 255
        private const val FADE_OPAQUE = 0
        private const val FADE_CYCLES = 50

        private val SURGE_COLUMNS = listOf(1477, 1478, 1479, 1480, 1481)
        private val FIREBALL_SOURCE = CoordGrid(1479, 10099)
        private val JUDGE_ARRIVAL = CoordGrid(1479, 10077)
        private val JUDGE_SPAWN = CoordGrid(1478, 10098)
        private val ARENA_RETURN_TILES =
            (1502..1504).flatMap { x -> (10085..10087).map { z -> CoordGrid(x, z) } }
    }
}
