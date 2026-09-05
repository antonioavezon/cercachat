package cl.antonioavezon.cercachat.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("cercachat_ajustes")

data class UiSettings(
    val textColor: Long = 0xFF1B1B1B,
    val chatBgColor: Long = 0xFFF3F6F4,
    val sentBubbleColor: Long = 0xFF0B6E4F,
    val receivedBubbleColor: Long = 0xFFFFFFFF,
    val fontScale: Float = 1.0f,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val maxFileMb: Int = 100,
    val localAlias: String = "",
) {
    companion object {
        val Default = UiSettings()
    }
}

class SettingsRepository(private val context: Context) {
    private val store get() = context.settingsStore

    val settings: Flow<UiSettings> = store.data.map { p ->
        UiSettings(
            textColor = p[TEXT] ?: UiSettings.Default.textColor,
            chatBgColor = p[BG] ?: UiSettings.Default.chatBgColor,
            sentBubbleColor = p[SENT] ?: UiSettings.Default.sentBubbleColor,
            receivedBubbleColor = p[RECV] ?: UiSettings.Default.receivedBubbleColor,
            fontScale = p[FONT] ?: UiSettings.Default.fontScale,
            soundEnabled = p[SOUND] ?: true,
            vibrationEnabled = p[VIB] ?: true,
            maxFileMb = p[MAX_MB] ?: 100,
            localAlias = p[ALIAS].orEmpty(),
        )
    }

    suspend fun current(): UiSettings = settings.first()

    suspend fun update(transform: (UiSettings) -> UiSettings) {
        val next = transform(current())
        store.edit { p ->
            p[TEXT] = next.textColor
            p[BG] = next.chatBgColor
            p[SENT] = next.sentBubbleColor
            p[RECV] = next.receivedBubbleColor
            p[FONT] = next.fontScale
            p[SOUND] = next.soundEnabled
            p[VIB] = next.vibrationEnabled
            p[MAX_MB] = next.maxFileMb
            p[ALIAS] = next.localAlias
        }
    }

    companion object {
        private val TEXT = longPreferencesKey("text")
        private val BG = longPreferencesKey("bg")
        private val SENT = longPreferencesKey("sent")
        private val RECV = longPreferencesKey("recv")
        private val FONT = floatPreferencesKey("font")
        private val SOUND = booleanPreferencesKey("sound")
        private val VIB = booleanPreferencesKey("vib")
        private val MAX_MB = intPreferencesKey("max_mb")
        private val ALIAS = stringPreferencesKey("alias")
    }
}
