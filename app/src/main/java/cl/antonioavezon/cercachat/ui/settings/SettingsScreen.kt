package cl.antonioavezon.cercachat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.antonioavezon.cercachat.storage.SettingsRepository
import cl.antonioavezon.cercachat.storage.UiSettings
import cl.antonioavezon.cercachat.ui.common.Contrast
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
) {
    val settings by settingsRepository.settings.collectAsState(initial = UiSettings.Default)
    val scope = rememberCoroutineScope()
    val text = Color(settings.textColor)
    val bg = Color(settings.chatBgColor)
    val sent = Color(settings.sentBubbleColor)
    val recv = Color(settings.receivedBubbleColor)
    val contrastIssue = Contrast.insufficient(text, sent) ||
        Contrast.insufficient(text, recv) ||
        Contrast.insufficient(text, bg)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Configuración", style = MaterialTheme.typography.headlineSmall)
        Text("Los cambios se guardan en el teléfono y no usan internet.", modifier = Modifier.padding(vertical = 8.dp))

        ColorSlider("Color del texto", settings.textColor) { v ->
            scope.launch { settingsRepository.update { it.copy(textColor = v) } }
        }
        ColorSlider("Color del fondo del chat", settings.chatBgColor) { v ->
            scope.launch { settingsRepository.update { it.copy(chatBgColor = v) } }
        }
        ColorSlider("Burbuja enviada", settings.sentBubbleColor) { v ->
            scope.launch { settingsRepository.update { it.copy(sentBubbleColor = v) } }
        }
        ColorSlider("Burbuja recibida", settings.receivedBubbleColor) { v ->
            scope.launch { settingsRepository.update { it.copy(receivedBubbleColor = v) } }
        }

        Text("Tamaño de letra")
        Slider(
            value = settings.fontScale,
            onValueChange = { v -> scope.launch { settingsRepository.update { it.copy(fontScale = v) } } },
            valueRange = 0.85f..1.6f,
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Sonido al recibir", Modifier.weight(1f))
            Switch(checked = settings.soundEnabled, onCheckedChange = { v ->
                scope.launch { settingsRepository.update { it.copy(soundEnabled = v) } }
            })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Vibración", Modifier.weight(1f))
            Switch(checked = settings.vibrationEnabled, onCheckedChange = { v ->
                scope.launch { settingsRepository.update { it.copy(vibrationEnabled = v) } }
            })
        }

        Text("Tamaño máximo de archivo: ${settings.maxFileMb} MB")
        Slider(
            value = settings.maxFileMb.toFloat(),
            onValueChange = { v -> scope.launch { settingsRepository.update { it.copy(maxFileMb = v.toInt()) } } },
            valueRange = 1f..200f,
        )

        Text("Vista previa", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(bg, RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            Text(
                "Mensaje enviado de prueba",
                color = text,
                fontSize = (16 * settings.fontScale).sp,
                modifier = Modifier.background(sent, RoundedCornerShape(12.dp)).padding(10.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Mensaje recibido de prueba 192.168.1.10",
                color = text,
                fontSize = (16 * settings.fontScale).sp,
                modifier = Modifier.background(recv, RoundedCornerShape(12.dp)).padding(10.dp),
            )
        }
        if (contrastIssue) {
            Text(
                "El contraste entre el texto y el fondo o las burbujas es bajo. Puede costar leerlo, sobre todo en terreno.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Button(
            onClick = { scope.launch { settingsRepository.update { UiSettings.Default.copy(localAlias = it.localAlias) } } },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 16.dp),
        ) { Text("Restaurar valores") }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
        ) { Text("Volver") }
    }
}

@Composable
private fun ColorSlider(label: String, value: Long, onChange: (Long) -> Unit) {
    val color = Color(value)
    Text(label)
    Text("Rojo")
    Slider(value = color.red, onValueChange = { onChange(repack(it, color.green, color.blue)) })
    Text("Verde")
    Slider(value = color.green, onValueChange = { onChange(repack(color.red, it, color.blue)) })
    Text("Azul")
    Slider(value = color.blue, onValueChange = { onChange(repack(color.red, color.green, it)) })
}

private fun repack(r: Float, g: Float, b: Float): Long {
    val ir = (r.coerceIn(0f, 1f) * 255).toInt()
    val ig = (g.coerceIn(0f, 1f) * 255).toInt()
    val ib = (b.coerceIn(0f, 1f) * 255).toInt()
    return (0xFFL shl 24) or (ir.toLong() shl 16) or (ig.toLong() shl 8) or ib.toLong()
}
