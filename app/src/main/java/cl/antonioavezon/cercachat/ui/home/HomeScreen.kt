package cl.antonioavezon.cercachat.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cl.antonioavezon.cercachat.session.SessionSnapshot
import cl.antonioavezon.cercachat.storage.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    snapshot: SessionSnapshot,
    settingsRepository: SettingsRepository,
    onShowQr: () -> Unit,
    onScanQr: () -> Unit,
    onSettings: () -> Unit,
    onSavedFiles: () -> Unit,
) {
    var alias by rememberSaveable { mutableStateOf("") }
    var aliasLoaded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        alias = withContext(Dispatchers.IO) { settingsRepository.current().localAlias }
        aliasLoaded = true
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
    ) {
        Text("CercaChat", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Habla con un compañero cercano sin internet, sin datos móviles y sin un router.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        OutlinedTextField(
            value = alias,
            onValueChange = {
                alias = it.take(40)
            },
            label = { Text("Alias local (opcional)") },
            supportingText = { Text("No hay cuentas ni registro. Solo se muestra al compañero de esta sesión.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LaunchedEffect(alias, aliasLoaded) {
            if (aliasLoaded) {
                settingsRepository.update { it.copy(localAlias = alias.trim()) }
            }
        }
        Spacer(Modifier.height(16.dp))
        HomeButton("Mostrar mi QR", "Crear red local y mostrar código QR", onShowQr)
        HomeButton("Escanear QR", "Unirse a la red del compañero", onScanQr)
        HomeButton("Configuración", "Colores, letra, sonido y límites", onSettings, outlined = true)
        HomeButton("Archivos guardados", "Abrir, exportar o eliminar archivos conservados", onSavedFiles, outlined = true)
        if (snapshot.retention != null) {
            Text(
                "Hay archivos pendientes de una conversación anterior.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun HomeButton(label: String, description: String, onClick: () -> Unit, outlined: Boolean = false) {
    val modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)
        .heightIn(min = 48.dp)
        .semantics { contentDescription = description }
    if (outlined) {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    }
}
