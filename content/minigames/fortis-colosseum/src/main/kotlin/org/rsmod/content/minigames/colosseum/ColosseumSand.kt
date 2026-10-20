package org.rsmod.content.minigames.colosseum

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

internal class SandTile(val loc: LocInfo, val permanent: Boolean, val addedAt: Int)

@Singleton
internal class ColosseumSand
@Inject
constructor(
    private val locRepo: LocRepository,
    private val worldQueues: WorldQueueList,
    private val random: GameRandom,
    private val hitModifier: StandardPlayerHitModifier,
    private val mapClock: MapClock,
) {
    private val sand = mutableMapOf<WaveState, MutableMap<CoordGrid, SandTile>>()
    private val burning = mutableSetOf<WaveState>()

    fun add(
        state: WaveState,
        player: Player,
        tile: CoordGrid,
        loc: String,
        permanent: Boolean,
        angle: LocAngle = LocAngle[random.of(LOC_ANGLES)],
        shape: LocShape = LocShape.CentrepieceStraight,
        replace: Boolean = false,
    ) {
        val tiles = sand.getOrPut(state) { mutableMapOf() }
        val existing = tiles[tile]
        if (existing != null && !replace) return
        existing?.let { locRepo.del(it.loc, Int.MAX_VALUE) }
        val added = locRepo.add(tile, loc, Int.MAX_VALUE, angle, shape)
        tiles[tile] = SandTile(added, permanent || existing?.permanent == true, mapClock.cycle)
        if (burning.add(state)) scheduleBurn(state, player)
    }

    fun has(state: WaveState, tile: CoordGrid): Boolean = sand[state]?.containsKey(tile) == true

    fun clearTemporary(state: WaveState) {
        val tiles = sand[state] ?: return
        val now = mapClock.cycle
        val cleared = tiles.values.filterNot { it.permanent }
        tiles.values.removeAll(cleared.toSet())
        cleared.filter { it.addedAt < now }.forEach(::clearLoc)
        val fresh = cleared.filter { it.addedAt >= now }
        if (fresh.isEmpty()) return
        worldQueues.add(1) {
            if (state in sand) {
                fresh.forEach(::clearLoc)
            } else {
                fresh.forEach { locRepo.del(it.loc, Int.MAX_VALUE) }
            }
        }
    }

    fun clearAll(state: WaveState) {
        val tiles = sand.remove(state) ?: return
        tiles.values.forEach(::clearLoc)
    }

    fun discard(state: WaveState) {
        val tiles = sand.remove(state) ?: return
        tiles.values.forEach { locRepo.del(it.loc, Int.MAX_VALUE) }
    }

    private fun clearLoc(tile: SandTile) {
        val loc = tile.loc
        locRepo.add(loc.coords, CLEARED_LOC, Int.MAX_VALUE, loc.angle, loc.shape)
    }

    private fun scheduleBurn(state: WaveState, player: Player) {
        worldQueues.add(1) {
            val tiles = sand[state]
            if (tiles.isNullOrEmpty()) {
                burning.remove(state)
                return@add
            }
            if (
                player.runState == RunState.WaveActive &&
                    player.hitpoints > 0 &&
                    player.coords in tiles
            ) {
                player.queueHit(1, HitType.Typeless, random.of(DAMAGE), hitModifier)
            }
            scheduleBurn(state, player)
        }
    }

    private companion object {
        private const val LOC_ANGLES = 4
        private const val CLEARED_LOC = "loc.invisible_type8_nonblocking"
        private val DAMAGE = 5..15
    }
}
