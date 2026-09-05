package cl.antonioavezon.cercachat.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import cl.antonioavezon.cercachat.CercaChatApp
import cl.antonioavezon.cercachat.MainActivity
import cl.antonioavezon.cercachat.R

class SessionForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE) {
            (application as CercaChatApp).container.session.closeConversation()
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Android puede matar el proceso; no prometemos continuidad.
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.notification_channel_session),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setSound(null, null) }
        )
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val close = PendingIntent.getService(
            this,
            2,
            Intent(this, SessionForegroundService::class.java).setAction(ACTION_CLOSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.notification_session_title))
            .setContentText(getString(R.string.notification_session_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, getString(R.string.notification_close), close)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(ID, notification)
        }
    }

    companion object {
        private const val CHANNEL = "cercachat_sesion"
        private const val ID = 1001
        const val ACTION_CLOSE = "cl.antonioavezon.cercachat.CLOSE_SESSION"

        fun start(context: Context) {
            val intent = Intent(context, SessionForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionForegroundService::class.java))
        }
    }
}
