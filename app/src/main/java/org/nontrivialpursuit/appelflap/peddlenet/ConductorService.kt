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
import androidx.core.app.NotificationCompat
import org.nontrivialpursuit.appelflap.Appelflap
import org.nontrivialpursuit.appelflap.NotificationIDs
import org.nontrivialpursuit.appelflap.R
import org.nontrivialpursuit.appelflap.webwrap.GeckoWrap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ConductorService : Service() {

    private val CHANNEL_ID = "ForegroundOrBackground"
    private val executor: ExecutorService = Executors.newFixedThreadPool(1)

    companion object {
        fun startService(context: Context, poke: Int, portno: Int? = null) {
            val startIntent = Intent(context, ConductorService::class.java)
            startIntent.putExtra("poke", poke)
            portno?.also {
                startIntent.putExtra("portno", it)
            }
            context.startForegroundService(startIntent)
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, ConductorService::class.java)
            context.stopService(stopIntent)
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val poke = intent.getIntExtra("poke", ServicePoke.BACKGROUNDED.ordinal)
        val conductor = Appelflap.get(this).conductor
        when (poke) {
            ServicePoke.START.ordinal -> {
                val portno: Int = intent.getIntExtra("portno", 0)
                if (portno !in 1..65535) throw RuntimeException("Invalid port number")
                createNotificationChannel()
                val notificationIntent = Intent(this, GeckoWrap::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, notificationIntent, FLAG_MUTABLE
                )
                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(resources.getString(R.string.conductor_service_notification_title_message))
                    .setSmallIcon(R.drawable.ic_radar).setContentIntent(pendingIntent).build()
                startForeground(NotificationIDs.CONDUCTOR_FOREGROUNDSERVICE.ordinal, notification)
                executor.execute {
                    conductor.also { it.start(portno) }
                }
            }
            ServicePoke.BACKGROUNDED.ordinal -> {
                conductor.backgrounded = true
            }
            ServicePoke.FOREGROUNDED.ordinal -> {
                conductor.backgrounded = false
            }
        }
        return START_NOT_STICKY
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
