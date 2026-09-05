package cl.antonioavezon.cercachat.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class HotspotReady(
    val ssid: String,
    val passphrase: String,
    val hostIpv4: String,
    val reservation: WifiManager.LocalOnlyHotspotReservation,
)

class HotspotException(message: String) : Exception(message)

class LocalHotspotHost(private val context: Context) {
    fun start(onResult: (Result<HotspotReady>) -> Unit) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectivity = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val handler = Handler(Looper.getMainLooper())
        try {
            wifi.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    DISCOVERY_EXECUTOR.execute {
                        try {
                            val (ssid, pass) = readCredentials(reservation)
                            val ip = waitForIpv4(connectivity)
                                ?: throw HotspotException(
                                    "El punto de acceso local se creó, pero no se pudo descubrir su IP. " +
                                        "No se asume una dirección fija. Reintenta o usa otro teléfono como anfitrión."
                                )
                            onResult(Result.success(HotspotReady(ssid, pass, ip, reservation)))
                        } catch (e: Exception) {
                            runCatching { reservation.close() }
                            onResult(Result.failure(e))
                        }
                    }
                }

                override fun onFailed(reason: Int) {
                    onResult(Result.failure(HotspotException(failureMessage(reason))))
                }

                override fun onStopped() {
                    // El cierre lo gestiona la sesión.
                }
            }, handler)
        } catch (e: SecurityException) {
            onResult(Result.failure(HotspotException("Falta el permiso de dispositivos cercanos o ubicación para crear la red local.")))
        } catch (e: Exception) {
            onResult(Result.failure(HotspotException(e.message ?: "No se pudo iniciar el punto de acceso local.")))
        }
    }

    suspend fun startSuspend(): HotspotReady = suspendCancellableCoroutine { cont ->
        start { result ->
            result.fold(
                onSuccess = { if (cont.isActive) cont.resume(it) },
                onFailure = { if (cont.isActive) cont.resumeWithException(it) },
            )
        }
    }

    private fun readCredentials(reservation: WifiManager.LocalOnlyHotspotReservation): Pair<String, String> {
        return if (Build.VERSION.SDK_INT >= 33) {
            val config = reservation.softApConfiguration
            val ssid = config.ssid?.trim('"') ?: ""
            val pass = config.passphrase ?: ""
            if (ssid.isBlank() || pass.length < 8) {
                throw HotspotException("El sistema no entregó credenciales usables del punto de acceso local.")
            }
            ssid to pass
        } else {
            @Suppress("DEPRECATION")
            val config = reservation.wifiConfiguration
                ?: throw HotspotException("El sistema no entregó la configuración del punto de acceso local.")
            val ssid = config.SSID?.trim('"').orEmpty()
            val pass = config.preSharedKey?.trim('"').orEmpty()
            if (ssid.isBlank() || pass.length < 8) {
                throw HotspotException("El sistema no entregó credenciales usables del punto de acceso local.")
            }
            ssid to pass
        }
    }

    private fun waitForIpv4(connectivity: ConnectivityManager): String? {
        repeat(12) { attempt ->
            HostEndpointDiscovery.discoverSoftApIpv4(connectivity)?.let { return it }
            if (attempt < 11) Thread.sleep(250)
        }
        return HostEndpointDiscovery.discoverSoftApIpv4(connectivity)
    }

    private fun failureMessage(reason: Int): String = when (reason) {
        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL ->
            "No hay un canal Wi-Fi disponible para el punto de acceso local."
        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED ->
            "Este teléfono no permite un punto de acceso solo local (política del fabricante o del administrador)."
        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE ->
            "El Wi-Fi está en un modo incompatible. Activa el Wi-Fi y desactiva el anclaje a red / tethering."
        WifiManager.LocalOnlyHotspotCallback.ERROR_GENERIC ->
            "El sistema rechazó el punto de acceso local. Prueba a activar el Wi-Fi o usar el otro teléfono para mostrar el QR."
        else ->
            "No se pudo crear la red local (código $reason). Este modelo puede no admitir LocalOnlyHotspot."
    }

    companion object {
        private val DISCOVERY_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "cercachat-hotspot").apply { isDaemon = true }
        }
    }
}
