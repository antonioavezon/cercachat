package cl.antonioavezon.cercachat.transport

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Descubre la IPv4 real del Soft AP de LocalOnlyHotspot.
 * No asume 192.168.43.1 (tethering) ni 192.168.49.1 (valor frecuente, no garantizado).
 *
 * Orden de preferencia:
 * 1. Interfaz con nombre de AP (ap0, swlan0, softap, wlan1/2) y dirección sitio-local.
 * 2. Red Wi-Fi local sin capacidad INTERNET expuesta por ConnectivityManager.
 * 3. Cualquier IPv4 sitio-local que no sea la STA principal (wlan0) si hay alternativas.
 */
object HostEndpointDiscovery {
    fun discoverSoftApIpv4(connectivityManager: ConnectivityManager?): String? {
        val fromInterfaces = fromNetworkInterfaces()
        preferred(fromInterfaces)?.let { return it.hostAddress }

        val fromNetworks = fromLocalOnlyNetworks(connectivityManager)
        if (fromNetworks != null) return fromNetworks

        return fromInterfaces.lastOrNull()?.second?.hostAddress
    }

    fun gatewayFrom(link: LinkProperties?): String? {
        if (link == null) return null
        val gateway = link.routes
            .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway as? Inet4Address
        return gateway?.hostAddress
    }

    fun ipv4From(link: LinkProperties?): List<String> {
        if (link == null) return emptyList()
        return link.linkAddresses.mapNotNull { addr ->
            (addr.address as? Inet4Address)?.takeIf { !it.isLoopbackAddress }?.hostAddress
        }
    }

    fun chooseClientTarget(qrHost: String, network: Network, connectivityManager: ConnectivityManager): String {
        val link = connectivityManager.getLinkProperties(network)
        val gateway = gatewayFrom(link)
        if (qrHost.isNotBlank()) return qrHost
        if (!gateway.isNullOrBlank()) return gateway
        throw IllegalStateException("No se pudo determinar la IP del anfitrión en la red local.")
    }

    private fun fromNetworkInterfaces(): List<Pair<String, Inet4Address>> {
        val found = mutableListOf<Pair<String, Inet4Address>>()
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return found
        for (nif in interfaces) {
            if (!nif.isUp || nif.isLoopback) continue
            val name = nif.name.lowercase()
            for (addr in nif.inetAddresses) {
                val v4 = addr as? Inet4Address ?: continue
                if (v4.isLoopbackAddress || !v4.isSiteLocalAddress) continue
                found += name to v4
            }
        }
        return found
    }

    private fun preferred(candidates: List<Pair<String, Inet4Address>>): Inet4Address? {
        val preferred = candidates.firstOrNull { (name, _) -> isApInterface(name) }
        return preferred?.second
    }

    private fun isApInterface(name: String): Boolean {
        return name.startsWith("ap") ||
            name.contains("softap") ||
            name.contains("swlan") ||
            name == "wlan1" ||
            name == "wlan2" ||
            name.startsWith("wlan1")
    }

    private fun fromLocalOnlyNetworks(connectivityManager: ConnectivityManager?): String? {
        if (connectivityManager == null) return null
        for (network in connectivityManager.allNetworks) {
            val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            val link = connectivityManager.getLinkProperties(network) ?: continue
            val ipv4 = ipv4From(link).firstOrNull() ?: continue
            return ipv4
        }
        return null
    }
}
