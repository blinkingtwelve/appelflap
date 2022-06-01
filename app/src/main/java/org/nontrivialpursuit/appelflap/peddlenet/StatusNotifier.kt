@file:JvmName("StatusNotifier")

package org.nontrivialpursuit.appelflap.peddlenet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.nontrivialpursuit.appelflap.BuildConfig
import org.nontrivialpursuit.appelflap.NotificationIDs
import org.nontrivialpursuit.appelflap.R
import org.nontrivialpursuit.appelflap.webwrap.GeckoWrap

class StatusNotifier(
        private val context: Context) {
    private val notificationmanager = context.getSystemService(NotificationManager::class.java)
    private val channel_created = create_statusnotification_channel()

    fun create_statusnotification_channel(): Boolean {
        if (channel_created) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationmanager.createNotificationChannel(NotificationChannel(
                NOTIFICATION_STATUS_CHANNEL,
                context.resources.getString(R.string.p2p_status_notification),
                NotificationManager.IMPORTANCE_DEFAULT
            ).also {
                it.vibrationPattern = longArrayOf(0, 20, 10, 20, 10, 20, 10, 20, 10, 20, 10, 20, 10, 20)
                it.setSound(null, null)
            })
        }
        return true
    }

    fun setStatus(text: String) {
        // TODO: only alert when this is relevant (when p2p AP mode is supported and configured to run)
        if (!(TENPLUS && BuildConfig.WIFI_P2P_ENABLED)) return
        create_statusnotification_channel()
        val notification = NotificationCompat.Builder(context, NOTIFICATION_STATUS_CHANNEL)
            .setContentTitle(context.resources.getString(R.string.conductor_needs_help)).setContentText(text)
            .setSmallIcon(R.drawable.ic_sentiment_very_dissatisfied_24dp).setContentIntent(
                PendingIntent.getActivity(
                    context, 0, Intent(context, GeckoWrap::class.java), 0
                )
            ).setAutoCancel(true).setOngoing(true).setOnlyAlertOnce(true).setUsesChronometer(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build()
        notificationmanager.notify(NotificationIDs.CONDUCTOR_STATUSMESSAGE.ordinal, notification)
    }

    fun clear() {
        notificationmanager.cancel(NotificationIDs.CONDUCTOR_STATUSMESSAGE.ordinal)
    }
}