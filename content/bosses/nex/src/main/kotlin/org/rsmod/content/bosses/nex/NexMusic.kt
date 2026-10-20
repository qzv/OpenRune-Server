package org.rsmod.content.bosses.nex

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.table.MusicRow
import org.rsmod.game.entity.Player

@Singleton
internal class NexMusic
@Inject
constructor(private val music: MusicPlayer, private val repo: MusicRepository) {
    private val smokeAndShadow: MusicRow? by lazy { track(SMOKE_AND_SHADOW_TRACK) }
    private val laterPhases: MusicRow? by lazy { track(LATER_PHASES_TRACK) }

    fun fightStart(player: Player) {
        smokeAndShadow?.let { row ->
            if (isLocked(player, row)) music.unlockAndPlay(player, row) else music.play(player, row)
        }
        laterPhases?.let { row -> if (isLocked(player, row)) music.unlock(player, row) }
    }

    fun playLaterPhases(player: Player) {
        laterPhases?.let { music.play(player, it) }
    }

    private fun isLocked(player: Player, row: MusicRow): Boolean =
        repo.forRow(row)?.canUnlock == true && !music.hasUnlocked(player, row)

    private fun track(name: String): MusicRow? =
        MusicRow.all().firstOrNull { it.displayname == name }

    private companion object {
        private const val SMOKE_AND_SHADOW_TRACK = "Zaros Zeitgeist"
        private const val LATER_PHASES_TRACK = "The Angel's Fury"
    }
}
