package cl.antonioavezon.cercachat

/**
 * Estado de proceso: sobrevive a rotación y recomposición, no a un inicio en frío.
 */
object AppProcessState {
    @Volatile
    var welcomeAccepted: Boolean = false
}
