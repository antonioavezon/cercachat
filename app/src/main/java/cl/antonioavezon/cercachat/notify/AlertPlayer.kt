package cl.antonioavezon.cercachat.notify

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import cl.antonioavezon.cercachat.R
import cl.antonioavezon.cercachat.storage.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlertPlayer(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    fun announceIncoming(chatVisible: Boolean) {
        scope.launch {
            val settings = settingsRepository.current()
            if (isDndBlocking()) return@launch
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val silent = audio.ringerMode == AudioManager.RINGER_MODE_SILENT
            if (chatVisible) {
                if (settings.soundEnabled && !silent && audio.ringerMode != AudioManager.RINGER_MODE_VIBRATE) {
                    playSound()
                }
                if (settings.vibrationEnabled && !silent) vibrate()
            } else {
                IncomingNotifier.notify(context, settings.soundEnabled && !silent)
                if (settings.vibrationEnabled && !silent) vibrate()
            }
        }
    }

    private fun isDndBlocking(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return when (nm.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_NONE,
            NotificationManager.INTERRUPTION_FILTER_ALARMS,
            -> true
            else -> false
        }
    }

    private fun playSound() {
        runCatching {
            MediaPlayer.create(context, R.raw.mensaje_recibido)?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setOnCompletionListener { it.release() }
                start()
            }
        }
    }

    private fun vibrate() {
        val effect = VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
        if (Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(VibratorManager::class.java)
            vm.defaultVibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(effect)
        }
    }
}
