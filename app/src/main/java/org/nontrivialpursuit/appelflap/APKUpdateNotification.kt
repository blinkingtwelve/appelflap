package org.nontrivialpursuit.appelflap

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class APKUpdateNotification(val context: Context, val origin: String? = null) {

    val CHANNEL_ID = "APKUpdateNotification"
    private val notiManager = NotificationManagerCompat.from(context)

    fun showInstallable(withIntent: Intent) {
        notiManager.notify(
            NotificationIDs.APKUPDATE_AVAILABLE.ordinal,
            NotificationCompat.Builder(context, CHANNEL_ID).setDefaults(Notification.DEFAULT_ALL)
                .setContentTitle(context.getString(R.string.apk_upgrade_available))
                .setContentText(context.getString(R.string.install_tap_positive)).setOngoing(true).setOnlyAlertOnce(true)
                .setSmallIcon(R.mipmap.ic_notification_swupgrade).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(PendingIntent.getActivity(context, 0, withIntent, PendingIntent.FLAG_IMMUTABLE)).setAutoCancel(true).build()
        )
    }

    fun showProgress(sofar: Long, max: Long) {
        val progress_promille = ((sofar/(max.toFloat())) * 1000).toInt()
        notiManager.notify(
            NotificationIDs.APKUPDATE_PROGRESS.ordinal,
            NotificationCompat.Builder(context, CHANNEL_ID).setDefaults(Notification.DEFAULT_ALL)
                .setContentTitle(context.getString(R.string.downloading_apk_upgrade))
                .setContentText(origin?.let{context.getString(R.string.downloading_apk_brought_to_you_by, origin) } ?: "")
                .setProgress(1000, progress_promille, false)
                .setSmallIcon(R.mipmap.ic_notification_swupgrade).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .build()
        )
    }

    fun cancelProgress() {
        notiManager.cancel(NotificationIDs.APKUPDATE_PROGRESS.ordinal)
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val thechannel = NotificationChannel(
                CHANNEL_ID, "Appelflap", NotificationManager.IMPORTANCE_DEFAULT
            )
            notiManager.createNotificationChannel(thechannel)
        }
    }
}