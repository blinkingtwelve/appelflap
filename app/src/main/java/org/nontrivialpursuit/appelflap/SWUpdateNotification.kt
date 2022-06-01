package org.nontrivialpursuit.appelflap

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.nontrivialpursuit.eekhoorn.KoekoeksNest
import org.nontrivialpursuit.ingeblikt.CacheType
import org.nontrivialpursuit.ingeblikt.injectables

const val CHANNEL_ID = "SWUpdateNotification"

class SWUpdateNotification(val context: Context, val koekoeksNest: KoekoeksNest) {

    private val notiManager = NotificationManagerCompat.from(context)


    fun poke() {
        when (injectables(koekoeksNest.getSubscriptions(mutex_permits = 0), koekoeksNest.listBundles()).first {
            it.type == CacheType.SWORK
        }) {
            null -> {
                cancel_showing()
            }
            else -> {
                show()
            }
        }
    }


    fun show() {
        notiManager.notify(
            NotificationIDs.SWUPDATE.ordinal,
            NotificationCompat.Builder(context, CHANNEL_ID).setDefaults(Notification.DEFAULT_ALL)
                .setContentTitle("Webapp upgrade available").setContentText("Tap to install").setOngoing(true).setOnlyAlertOnce(true)
                .setSmallIcon(R.mipmap.ic_notification_swupgrade).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setContentIntent(
                    Appelflap.get(context).getRelaunchPendingIntent(pid = PID_NO_PID)
                ).setAutoCancel(true).build()
        )
    }


    fun cancel_showing() {
        notiManager.cancel(NotificationIDs.SWUPDATE.ordinal)
    }


    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val thechannel = NotificationChannel(
                CHANNEL_ID, "Appelflap", NotificationManager.IMPORTANCE_HIGH
            )
            notiManager.createNotificationChannel(thechannel)
        }
    }
}