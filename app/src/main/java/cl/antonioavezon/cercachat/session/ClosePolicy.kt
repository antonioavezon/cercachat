package cl.antonioavezon.cercachat.session

/**
 * Reglas de cierre y retención, sin Android, para pruebas unitarias.
 */
object ClosePolicy {
    fun shouldPromptRetention(managedFileCount: Int): Boolean = managedFileCount > 0

    fun invalidateQr(previous: String?, closed: Boolean): String? =
        if (closed) null else previous

    fun canReuseToken(tokenUsed: Boolean, closed: Boolean): Boolean =
        !tokenUsed && !closed

    fun pendingRetentionAfterCrash(hadManagedFiles: Boolean, decisionTaken: Boolean): Boolean =
        hadManagedFiles && !decisionTaken
}
