package cl.antonioavezon.cercachat.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import cl.antonioavezon.cercachat.protocol.QrPayload
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ClientNetwork(
    val network: Network,
    val hostIpv4: String,
)

class WifiClientException(message: String) : Exception(message)

class WifiSpecifierClient(private val context: Context) {
    private var callback: ConnectivityManager.NetworkCallback? = null

    suspend fun connect(payload: QrPayload): ClientNetwork = suspendCancellableCoroutine { cont ->
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // Solo SSID exacto + PSK: la API no permite combinar SSID y patrón.
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(payload.ssid)
            .setWpa2Passphrase(payload.psk)
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!cont.isActive) return
                try {
                    val host = HostEndpointDiscovery.chooseClientTarget(payload.host, network, cm)
                    cont.resume(ClientNetwork(network, host))
                } catch (e: Exception) {
                    cont.resumeWithException(
                        WifiClientException(
                            e.message ?: "Conectado al Wi-Fi local, pero no se descubrió el anfitrión."
                        )
                    )
                }
            }

            override fun onUnavailable() {
                if (cont.isActive) {
                    cont.resumeWithException(
                        WifiClientException(
                            "No se pudo unir a la red del QR. El sistema lo rechazó, el QR caducó o este teléfono no admite WifiNetworkSpecifier."
                        )
                    )
                }
            }
        }
        callback = cb
        try {
            cm.requestNetwork(request, cb)
        } catch (e: SecurityException) {
            if (cont.isActive) {
                cont.resumeWithException(
                    WifiClientException("Falta el permiso de dispositivos cercanos o ubicación para unirse a la red local.")
                )
            }
            return@suspendCancellableCoroutine
        } catch (e: Exception) {
            if (cont.isActive) {
                cont.resumeWithException(
                    WifiClientException(e.message ?: "No se pudo solicitar la red local.")
                )
            }
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation { release() }
    }

    fun release() {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        callback?.let {
            runCatching { cm.unregisterNetworkCallback(it) }
        }
        callback = null
    }
}
