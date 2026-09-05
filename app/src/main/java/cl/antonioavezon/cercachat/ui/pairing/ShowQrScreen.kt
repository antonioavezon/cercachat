package cl.antonioavezon.cercachat.ui.pairing

import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cl.antonioavezon.cercachat.session.ConnectionPhase
import cl.antonioavezon.cercachat.session.SessionController
import cl.antonioavezon.cercachat.session.SessionSnapshot
import cl.antonioavezon.cercachat.ui.common.Permissions
import cl.antonioavezon.cercachat.ui.common.QrBitmap
import kotlinx.coroutines.delay

@Composable
fun ShowQrScreen(
    snapshot: SessionSnapshot,
    session: SessionController,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var permissionError by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) {
            permissionError = null
            if (locationBlocked(context)) {
                permissionError = "Activa la ubicación del sistema. En Android 10 a 12 el Wi-Fi local la requiere."
            } else {
                session.startHost()
            }
        } else {
            permissionError = "Se necesitan permisos de dispositivos cercanos o ubicación para crear la red local."
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(Permissions.hostPairing())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Mostrar mi QR", style = MaterialTheme.typography.headlineSmall)
        Text(
            estadoTexto(snapshot),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        permissionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        snapshot.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        when {
            snapshot.phase == ConnectionPhase.Preparing -> CircularProgressIndicator()
            snapshot.qrContent != null -> {
                val bitmap = remember(snapshot.qrContent) { QrBitmap.render(snapshot.qrContent) }
                Image(
                    bitmap = bitmap,
                    contentDescription = "Código QR de emparejamiento CercaChat",
                    modifier = Modifier.size(280.dp),
                )
                QrCountdown(expiresAt = snapshot.qrExpiresAtMs)
                snapshot.hostIp?.let {
                    Text("Anfitrión en $it", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "El compañero debe escanear este código. No incluye internet ni tethering de datos.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { session.regenerateQr() },
            enabled = snapshot.qrContent != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "Regenerar código QR" },
        ) { Text("Regenerar QR") }
        Button(
            onClick = {
                permissionError = null
                launcher.launch(Permissions.hostPairing())
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
        ) { Text("Reintentar") }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
        ) { Text("Cancelar") }
    }
}

@Composable
private fun QrCountdown(expiresAt: Long?) {
    if (expiresAt == null) return
    var remaining by remember { mutableStateOf(expiresAt - System.currentTimeMillis()) }
    LaunchedEffect(expiresAt) {
        while (true) {
            remaining = expiresAt - System.currentTimeMillis()
            if (remaining <= 0) break
            delay(500)
        }
    }
    val seconds = (remaining / 1000).coerceAtLeast(0)
    Text(
        if (remaining <= 0) "Este QR caducó. Regenera uno nuevo."
        else "Caduca en ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

internal fun estadoTexto(snapshot: SessionSnapshot): String = when (snapshot.phase) {
    ConnectionPhase.Idle -> "Listo para preparar la conexión"
    ConnectionPhase.Preparing -> "Preparando conexión"
    ConnectionPhase.WaitingPeer -> "Esperando compañero"
    ConnectionPhase.Connecting -> "Conectando"
    ConnectionPhase.Connected -> "Conectado"
    ConnectionPhase.Error -> "Error"
}

internal fun locationBlocked(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT >= 33) return false
    val lm = context.getSystemService(LocationManager::class.java)
    return lm?.isLocationEnabled != true
}
