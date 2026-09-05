package cl.antonioavezon.cercachat.audio

import android.media.MediaPlayer
import java.io.File

class VoicePlayer {
    private var player: MediaPlayer? = null
    var playingPath: String? = null
        private set

    fun toggle(file: File) {
        if (playingPath == file.absolutePath && player?.isPlaying == true) {
            pause()
            return
        }
        release()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener { release() }
            prepare()
            start()
        }
        playingPath = file.absolutePath
    }

    fun pause() {
        runCatching { player?.pause() }
    }

    fun release() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        playingPath = null
    }
}
