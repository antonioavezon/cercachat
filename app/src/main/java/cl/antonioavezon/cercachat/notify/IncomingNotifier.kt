package cl.antonioavezon.cercachat.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import cl.antonioavezon.cercachat.MainActivity
import cl.antonioavezon.cercachat.R

object IncomingNotifier {
    private const val CHANNEL = "cercachat_mensajes"
    private const val ID = 2002

    fun notify(context: Context, withSound: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        ensureChannel(context, nm, withSound)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("CercaChat")
            .setContentText("Nuevo mensaje o archivo recibido")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(!withSound)
            .build()
        nm.notify(ID, notification)
    }

    private fun ensureChannel(context: Context, nm: NotificationManager, withSound: Boolean) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.notification_channel_messages),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            setSound(
                if (withSound) Uri.parse("android.resource://${context.packageName}/${R.raw.mensaje_recibido}") else null,
                attrs,
            )
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }
}
