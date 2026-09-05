package cl.antonioavezon.cercachat.ui.pairing

import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.antonioavezon.cercachat.session.ConnectionPhase
import cl.antonioavezon.cercachat.session.SessionController
import cl.antonioavezon.cercachat.session.SessionSnapshot
import cl.antonioavezon.cercachat.ui.common.Permissions
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ScanQrScreen(
    snapshot: SessionSnapshot,
    session: SessionController,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var permissionError by remember { mutableStateOf<String?>(null) }
    var cameraOn by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) {
            permissionError = null
            if (locationBlocked(context)) {
                permissionError = "Activa la ubicación del sistema. En Android 10 a 12 el Wi-Fi local la requiere."
            } else {
                cameraOn = true
            }
        } else {
            permissionError = "Se necesitan cámara y permiso de dispositivos cercanos o ubicación."
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        launcher.launch(Permissions.clientPairing())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Escanear QR", style = MaterialTheme.typography.headlineSmall)
        Text(estadoTexto(snapshot), modifier = Modifier.padding(vertical = 8.dp))
        permissionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        snapshot.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (cameraOn && snapshot.phase == ConnectionPhase.Idle || cameraOn && snapshot.phase == ConnectionPhase.Error) {
            QrCamera(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onDetected = { raw ->
                    cameraOn = false
                    session.startClient(raw)
                },
            )
        } else {
            Spacer(Modifier.weight(1f))
            if (snapshot.phase == ConnectionPhase.Connecting || snapshot.phase == ConnectionPhase.Preparing) {
                CircularProgressIndicator()
                Text("Acepta el diálogo del sistema para unirte a la red local.", modifier = Modifier.padding(top = 12.dp))
            }
        }

        Button(
            onClick = {
                session.cancelPairing()
                cameraOn = true
                launcher.launch(Permissions.clientPairing())
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Reintentar") }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
        ) { Text("Cancelar") }
    }
}

@Composable
private fun QrCamera(modifier: Modifier, onDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val handled = remember { AtomicBoolean(false) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                val reader = MultiFormatReader()
                analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { image ->
                    decodeQr(image, reader)?.let { text ->
                        if (handled.compareAndSet(false, true)) {
                            onDetected(text)
                        }
                    }
                    image.close()
                }
                provider.unbindAll()
                runCatching {
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }
}

private fun decodeQr(image: ImageProxy, reader: MultiFormatReader): String? {
    return try {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        val source = PlanarYUVLuminanceSource(
            data,
            plane.rowStride,
            image.height,
            0,
            0,
            image.width,
            image.height,
            false,
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        reader.decode(bitmap).text
    } catch (_: Exception) {
        reader.reset()
        null
    }
}
