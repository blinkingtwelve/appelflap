package org.nontrivialpursuit.appelflap.peddlenet.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import org.nontrivialpursuit.appelflap.Logger
import org.nontrivialpursuit.appelflap.peddlenet.*
import org.nontrivialpursuit.ingeblikt.Statehash
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

fun ScanResult.is_peer(): Boolean {
    return PEER_SSID_REX.matches(this.SSID)
    // TODO: check HMAC
}

fun ScanResult.to_APPeer(): APPeer? {
    return PEER_SSID_REX.matchEntire(this.SSID)?.let {
        val (nodeid, statehash, _) = it.destructured
        // TODO: somewhat-verify that it's "one of ours" using the HMAC part.
        return APPeer(
            nodeid, Statehash(
                statehash
            ), this
        )
    }
}

class APScanner(
        internal val conductor: Conductor, ) : ServiceHandler, BroadcastReceiver() {
    override val log = Logger(this)
    override var is_running = false
    val schedxecutor = Executors.newScheduledThreadPool(1)
    var scan_trigger_handler: ScheduledFuture<*>? = null

    override fun start(): Boolean {
        is_running = true
        conductor.context.registerReceiver(
            this, IntentFilter().apply { addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) },
        )
        scan_trigger_handler = schedxecutor.scheduleWithFixedDelay(
            {
                @Suppress("DEPRECATION") conductor.wifimanager.startScan()
            }, 0L, APSCAN_POLL_INTERVAL, TimeUnit.SECONDS
        )
        return true
    }

    override fun stop() {
        scan_trigger_handler?.cancel(true)
        try {
            conductor.context.unregisterReceiver(this)
        } catch (e: RuntimeException) {
            // pass, wasn't regged
        }
        is_running = false
    }

    override fun restart() {
        stop()
        start()
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                conductor.wifimanager.scanResults.filter {
                    it.is_peer()
                }.forEach {
                    it.to_APPeer()?.also {
                        conductor.update_ap_inventory(it)
                    }
                }
            }
        }
    }
}

