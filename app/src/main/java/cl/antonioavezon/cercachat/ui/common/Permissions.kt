package cl.antonioavezon.cercachat.ui.common

import android.Manifest
import android.os.Build

object Permissions {
    fun wifiNearby(): Array<String> = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }

    fun camera(): Array<String> = arrayOf(Manifest.permission.CAMERA)

    fun microphone(): Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    fun notifications(): Array<String> = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }

    fun hostPairing(): Array<String> = wifiNearby() + notifications()

    fun clientPairing(): Array<String> = wifiNearby() + camera() + notifications()
}
