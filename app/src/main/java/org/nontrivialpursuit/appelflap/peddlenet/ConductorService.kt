package org.nontrivialpursuit.appelflap.peddlenet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.nontrivialpursuit.appelflap.Appelflap
import org.nontrivialpursuit.appelflap.Logger
import org.nontrivialpursuit.appelflap.NotificationIDs
import org.nontrivialpursuit.appelflap.R
import org.nontrivialpursuit.appelflap.webwrap.GeckoWrap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ConductorService : Service() {

    private val CHANNEL_ID = "ForegroundOrBackground"
    private val executor: ExecutorService = Executors.newFixedThreadPool(1)

    companion object {
        val log = Logger(this)

        @Synchronized
        fun startService(context: Context, portno: Int? = null) {
            log.i("startService: poked with portno ${portno}")
            val startIntent = Intent(context, ConductorService::class.java)
            portno?.also {
                startIntent.putExtra("portno", it)
            }
            startIntent.putExtra("intent_created_at", SystemClock.elapsedRealtime())
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, ConductorService::class.java)
            context.stopService(stopIntent)
        }
    }

    @Synchronized
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val intent_created_at = intent.getLongExtra("intent_created_at", 0L)
        log.i("onStartCommand: poked with startId ${startId} created ${SystemClock.elapsedRealtime() - intent_created_at} ms ago")
        val conductor = Appelflap.get(this).conductor
        val portno: Int = intent.getIntExtra("portno", 0)
        if (portno !in 1..65535) throw RuntimeException("Invalid port number")
        createNotificationChannel()
        val notificationIntent = Intent(this, GeckoWrap::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, FLAG_MUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(resources.getString(R.string.conductor_service_notification_title_message)).setSmallIcon(R.drawable.ic_radar)
            .setContentIntent(pendingIntent).build()
        startForeground(NotificationIDs.CONDUCTOR_FOREGROUNDSERVICE.ordinal, notification)
        (SystemClock.elapsedRealtime() - intent_created_at).takeIf{it > SERVICE_START_DELAY_WARN_MS}?.also{
            log.w("onStartCommand: startForeground poked within ${it} ms of startForegroundService")
        }
        executor.execute {
            conductor.also { it.start(portno) }
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Appelflap.get(this).conductor.stop()
        executor.shutdownNow()
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "Foreground Service Channel", NotificationManager.IMPORTANCE_LOW
            )
            val notificationmanager = getSystemService(NotificationManager::class.java)
            notificationmanager!!.createNotificationChannel(serviceChannel)
        }
    }
}
