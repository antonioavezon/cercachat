package cl.antonioavezon.cercachat.storage

import android.content.Context
import cl.antonioavezon.cercachat.protocol.FlatJson
import java.io.File

class SavedFilesRepository(private val context: Context) {
    private val index get() = File(context.filesDir, "conservados_index.jsonl")

    fun list(): List<ManagedFile> {
        if (!index.exists()) return emptyList()
        return index.readLines().mapNotNull { line ->
            runCatching {
                val m = FlatJson.parse(line)
                val path = FlatJson.string(m, "path")
                if (!File(path).exists()) return@runCatching null
                ManagedFile(
                    id = FlatJson.string(m, "id"),
                    sessionId = FlatJson.string(m, "sid"),
                    displayName = FlatJson.string(m, "name"),
                    size = FlatJson.long(m, "size"),
                    path = path,
                    mime = FlatJson.string(m, "mime"),
                    kind = FlatJson.string(m, "kind"),
                    durationMs = FlatJson.optString(m, "dur")?.toLongOrNull(),
                )
            }.getOrNull()
        }
    }

    fun addAll(files: List<ManagedFile>) {
        index.appendText(files.joinToString("") { f ->
            FlatJson.obj(
                "id" to f.id,
                "sid" to f.sessionId,
                "name" to f.displayName,
                "size" to f.size,
                "path" to f.path,
                "mime" to f.mime,
                "kind" to f.kind,
                "dur" to (f.durationMs ?: 0L),
            ) + "\n"
        })
    }

    fun remove(id: String) {
        val remaining = list().filterNot { it.id == id }
        index.writeText(remaining.joinToString("") { f ->
            FlatJson.obj(
                "id" to f.id,
                "sid" to f.sessionId,
                "name" to f.displayName,
                "size" to f.size,
                "path" to f.path,
                "mime" to f.mime,
                "kind" to f.kind,
                "dur" to (f.durationMs ?: 0L),
            ) + "\n"
        })
    }
}
