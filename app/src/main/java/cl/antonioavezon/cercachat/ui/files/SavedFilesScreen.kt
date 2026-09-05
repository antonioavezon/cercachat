package cl.antonioavezon.cercachat.ui.files

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import cl.antonioavezon.cercachat.storage.IncomingFileStore
import cl.antonioavezon.cercachat.storage.ManagedFile
import cl.antonioavezon.cercachat.storage.SavedFilesRepository
import cl.antonioavezon.cercachat.ui.formatSize
import java.io.File

@Composable
fun SavedFilesScreen(
    savedFiles: SavedFilesRepository,
    fileStore: IncomingFileStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(savedFiles.list()) }
    var exportTarget by remember { mutableStateOf<ManagedFile?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val src = exportTarget
        exportTarget = null
        if (uri != null && src != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    File(src.path).inputStream().use { it.copyTo(out) }
                }
                Toast.makeText(context, "Exportado. Esa copia ya no la administra CercaChat.", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
    ) {
        Text("Archivos guardados", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Solo copias conservadas por CercaChat. Abrir no ejecuta el archivo automáticamente de forma especial: usa el visor que elijas.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            items(items, key = { it.id }) { file ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(file.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(formatSize(file.size), style = MaterialTheme.typography.bodySmall)
                    Row {
                        OutlinedButton(
                            onClick = { open(context, File(file.path)) },
                            modifier = Modifier.heightIn(min = 48.dp).padding(end = 6.dp),
                        ) { Text("Abrir") }
                        OutlinedButton(
                            onClick = {
                                exportTarget = file
                                exporter.launch(file.displayName)
                            },
                            modifier = Modifier.heightIn(min = 48.dp).padding(end = 6.dp),
                        ) { Text("Exportar") }
                        OutlinedButton(
                            onClick = {
                                fileStore.deleteManaged(file.path)
                                savedFiles.remove(file.id)
                                items = savedFiles.list()
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Eliminar") }
                    }
                }
            }
        }
        if (items.isEmpty()) {
            Text("No hay archivos conservados.")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Volver")
        }
    }
}

private fun open(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Abrir archivo")) }
}
