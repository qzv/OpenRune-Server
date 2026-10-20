package org.rsmod.content.bosses.yama

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.table.MusicRow
import org.rsmod.game.entity.Player

@Singleton
internal class YamaMusic
@Inject
constructor(private val music: MusicPlayer, private val repo: MusicRepository) {
    private val track: MusicRow? by lazy {
        MusicRow.all().firstOrNull { it.displayname == TRACK_NAME }
    }

    fun play(player: Player) {
        val row = track ?: return
        val unlockable = repo.forRow(row)?.canUnlock ?: return
        if (unlockable) music.unlockAndPlay(player, row) else music.play(player, row)
    }

    private companion object {
        private const val TRACK_NAME = "Sign Here"
    }
}
