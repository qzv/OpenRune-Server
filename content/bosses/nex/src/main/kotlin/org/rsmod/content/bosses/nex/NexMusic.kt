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
    private val smokeAndShadow: MusicRow by lazy { MusicRow.getRow(SMOKE_AND_SHADOW_TRACK) }
    private val laterPhases: MusicRow by lazy { MusicRow.getRow(LATER_PHASES_TRACK) }

    fun fightStart(player: Player) {
        if (isLocked(player, smokeAndShadow)) {
            music.unlockAndPlay(player, smokeAndShadow)
        } else {
            music.play(player, smokeAndShadow)
        }
        if (isLocked(player, laterPhases)) music.unlock(player, laterPhases)
    }

    fun playLaterPhases(player: Player) {
        music.play(player, laterPhases)
    }

    private fun isLocked(player: Player, row: MusicRow): Boolean =
        repo.forRow(row)?.canUnlock == true && !music.hasUnlocked(player, row)

    private companion object {
        private const val SMOKE_AND_SHADOW_TRACK = "dbrow.music_zaros_zeitgeist"
        private const val LATER_PHASES_TRACK = "dbrow.music_the_angels_fury"
    }
}
