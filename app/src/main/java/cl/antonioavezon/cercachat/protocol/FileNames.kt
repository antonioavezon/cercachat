package cl.antonioavezon.cercachat.protocol

object FileNames {
    private val unsafe = Regex("[^\\p{L}\\p{N}._\\- ()]+")

    fun sanitize(original: String): String {
        val leaf = original
            .replace('\\', '/')
            .substringAfterLast('/')
            .trim()
        val cleaned = unsafe.replace(leaf, "_")
            .trim('.', ' ', '_')
            .take(120)
        val safe = cleaned.ifBlank { "archivo" }
        return if (looksLikeTraversal(safe)) "archivo" else safe
    }

    fun unique(desired: String, existingLowercase: Set<String>): String {
        val base = sanitize(desired)
        if (base.lowercase() !in existingLowercase) return base
        val dot = base.lastIndexOf('.')
        val name = if (dot > 0) base.substring(0, dot) else base
        val ext = if (dot > 0) base.substring(dot) else ""
        var i = 2
        while (true) {
            val candidate = "$name ($i)$ext"
            if (candidate.lowercase() !in existingLowercase) return candidate
            i++
        }
    }

    fun looksLikeTraversal(name: String): Boolean {
        return name.contains("..") || name.contains('/') || name.contains('\\')
    }
}
