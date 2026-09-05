package cl.antonioavezon.cercachat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val scheme = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    onPrimary = Color.White,
    secondary = Color(0xFF1B4332),
    background = Color(0xFFF3F6F4),
    surface = Color.White,
    error = Color(0xFFB3261E),
)

@Composable
fun CercaChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
