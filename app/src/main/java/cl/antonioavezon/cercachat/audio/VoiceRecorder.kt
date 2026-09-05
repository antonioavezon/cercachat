package cl.antonioavezon.cercachat.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt: Long = 0
    val isRecording: Boolean get() = recorder != null

    fun start(): File {
        stopInternal(discard = true)
        val dest = File(context.cacheDir, "nota_${System.currentTimeMillis()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setAudioEncodingBitRate(96_000)
        rec.setAudioSamplingRate(44_100)
        rec.setOutputFile(dest.absolutePath)
        rec.prepare()
        rec.start()
        recorder = rec
        file = dest
        startedAt = System.currentTimeMillis()
        return dest
    }

    fun stop(): Pair<File, Long>? {
        val dest = file ?: return null
        val duration = (System.currentTimeMillis() - startedAt).coerceAtLeast(0)
        stopInternal(discard = false)
        return dest to duration
    }

    fun discard() {
        stopInternal(discard = true)
    }

    private fun stopInternal(discard: Boolean) {
        runCatching {
            recorder?.apply {
                stop()
                reset()
                release()
            }
        }
        recorder = null
        if (discard) {
            file?.delete()
        }
        file = null
    }
}
