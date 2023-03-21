package org.nontrivialpursuit.appelflap.peddlenet.services

import org.nontrivialpursuit.appelflap.*
import org.nontrivialpursuit.appelflap.peddlenet.*
import org.nontrivialpursuit.ingeblikt.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

// We can't install APK upgrades signed with a debugging certificate (yes through ADB, but not through the Android APIs).
val CAN_AUTOUPDATE = !BuildConfig.DEBUG
const val APKGRAB_MUTEX_NAME = "apk_downloader"

class AppUpdater(private val conductor: Conductor) : ServiceHandler {

    override val log = Logger(this)
    override var is_running = false
    val schedxecutor: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    var executor: ExecutorService? = null
    var apk_downloader: ScheduledFuture<*>? = null
    val updater = AppUpdate.getInstance(conductor.context)

    fun maybe_grab_apk() {
        Lockchest.runWith(name = APKGRAB_MUTEX_NAME, run = {
            conductor.get_bonjour_peer_candidates()
                .filter { it.appversion > BuildConfig.VERSION_CODE && it.appversion > updater.max_available_version() }
                .maxByOrNull { it.appversion }?.let { peer ->
                    updater.grab_apk(peer, APKUpdateNotification(conductor.context, origin = "${peer.nodeid} @ ${peer.address.hostAddress}"))
                }
        }, block = false)
    }

    fun maybe_show_update_notification() {
        updater.getUpgradeIntent()?.also {
            APKUpdateNotification(conductor.context).showInstallable(it)
        }
    }

    override fun start(): Boolean {
        is_running = true
        updater.cleanup()
        executor = Executors.newSingleThreadExecutor()
        if (CAN_AUTOUPDATE) {
            apk_downloader = schedxecutor.scheduleWithFixedDelay(
                ::maybe_grab_apk, 0L, BONJOUR_RESOLVE_INTERVAL, TimeUnit.SECONDS
            )
        }
        schedxecutor.scheduleWithFixedDelay(
            ::maybe_show_update_notification, 0L, BONJOUR_RESOLVE_INTERVAL, TimeUnit.SECONDS
        )
        return true
    }

    override fun stop() {
        apk_downloader?.cancel(true)
        executor?.shutdownNow()
    }

    override fun restart() {
        stop()
        start()
    }

}