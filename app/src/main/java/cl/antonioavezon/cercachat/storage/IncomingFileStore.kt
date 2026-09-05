package cl.antonioavezon.cercachat.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import cl.antonioavezon.cercachat.protocol.FileNames
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class ManagedFile(
    val id: String,
    val sessionId: String,
    val displayName: String,
    val size: Long,
    val path: String,
    val mime: String,
    val kind: String,
    val durationMs: Long?,
)

class IncomingFileStore(private val context: Context) {
    private val recibidos get() = File(context.filesDir, "recibidos").apply { mkdirs() }
    private val conservados get() = File(context.filesDir, "conservados").apply { mkdirs() }
    private val pendingFile get() = File(context.filesDir, "pending_retention.txt")

    fun sessionDir(sessionId: String): File {
        val safe = FileNames.sanitize(sessionId).ifBlank { "sesion" }
        return File(recibidos, safe).apply { mkdirs() }
    }

    fun createIncoming(sessionId: String, suggestedName: String): File {
        val dir = sessionDir(sessionId)
        val existing = dir.listFiles()?.map { it.name.lowercase() }?.toSet().orEmpty()
        val name = FileNames.unique(suggestedName, existing)
        return File(dir, name)
    }

    fun availableBytes(): Long {
        val stat = StatFs(context.filesDir.absolutePath)
        return stat.availableBytes
    }

    fun listSessionFiles(sessionId: String): List<File> {
        val dir = sessionDir(sessionId)
        return dir.listFiles()?.filter { it.isFile }?.toList().orEmpty()
    }

    fun markPending(sessionId: String) {
        pendingFile.writeText(sessionId)
    }

    fun clearPending() {
        pendingFile.delete()
    }

    fun pendingSessionId(): String? {
        if (!pendingFile.exists()) return null
        return pendingFile.readText().trim().ifBlank { null }
    }

    fun keepSession(sessionId: String): List<ManagedFile> {
        val files = listSessionFiles(sessionId)
        val kept = mutableListOf<ManagedFile>()
        files.forEach { src ->
            val id = UUID.randomUUID().toString()
            val destDir = File(conservados, id).apply { mkdirs() }
            val dest = File(destDir, src.name)
            src.copyTo(dest, overwrite = false)
            kept += ManagedFile(
                id = id,
                sessionId = sessionId,
                displayName = src.name,
                size = dest.length(),
                path = dest.absolutePath,
                mime = "application/octet-stream",
                kind = if (src.name.endsWith(".m4a", true)) "voice" else "file",
                durationMs = null,
            )
        }
        deleteSession(sessionId)
        clearPending()
        return kept
    }

    fun deleteSession(sessionId: String) {
        sessionDir(sessionId).deleteRecursively()
        clearPending()
    }

    fun deleteManaged(path: String) {
        val file = File(path)
        val root = conservados.canonicalFile
        val target = file.canonicalFile
        if (target.path.startsWith(root.path + File.separator) || target == root) {
            target.parentFile?.deleteRecursively()
        }
    }

    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun exportCopy(src: File): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        // Exportación real se hace con Storage Access Framework desde la UI.
        // Esta copia temporal solo vive en caché privada.
        val cache = File(context.cacheDir, "export").apply { mkdirs() }
        val dest = File(cache, src.name)
        src.copyTo(dest, overwrite = true)
        return dest
    }
}
