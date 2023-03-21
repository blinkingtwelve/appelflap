@file:JvmName("p2pDiscoverer")

package org.nontrivialpursuit.appelflap.peddlenet.services

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import org.nontrivialpursuit.appelflap.Logger
import org.nontrivialpursuit.appelflap.peddlenet.Conductor
import org.nontrivialpursuit.appelflap.peddlenet.P2P_ERRORS
import org.nontrivialpursuit.appelflap.peddlenet.ServiceHandler

class p2pDiscoverer(
        private val conductor: Conductor, ) : ServiceHandler, BroadcastReceiver(), WifiP2pManager.PeerListListener {

    private var p2p_discovery_running = false
    override val log = Logger(this)
    override var is_running = false

    private val actionlistener = object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
            log.v("p2p discoverpeers call: success")
            p2p_discovery_running = true
        }

        override fun onFailure(why: Int) {
            log.w("p2p discoverpeers call: failed, backgrounded: ${conductor.backgrounded}, reason: ${P2P_ERRORS[why]}")
            p2p_discovery_running = false
            is_running = false
        }
    }

    @SuppressLint("MissingPermission")
    override fun start(): Boolean {
        if (!conductor.p2p_enabled) return false
        is_running = true
        conductor.context.registerReceiver(this, IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
        })

        conductor.p2pmanager.discoverPeers(
            conductor.channel, actionlistener
        )
        return true
    }

    override fun stop() {
        conductor.p2pmanager.stopPeerDiscovery(conductor.channel, actionlistener)
        try {
            conductor.context.unregisterReceiver(this)
        } catch (_: IllegalArgumentException) {
        }  // pass; wasn't registered
        is_running = false
    }

    override fun restart() {
        stop()
        start()
    }


    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                log.v("P2P peers changed broadcast event, requesting peers")
                conductor.p2pmanager.requestPeers(conductor.channel, this)
            }
            WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                when (intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1)) {
                    WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED -> {
                        p2p_discovery_running = true
                        log.v("p2p discovery started")
                    }
                    WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED -> {
                        p2p_discovery_running = false
                        log.w("p2p discovery stopped")
                        is_running = false
                    }
                }
            }
        }
    }

    override fun onPeersAvailable(devlist: WifiP2pDeviceList) {
        log.i("P2P peer discovery result: ${devlist.deviceList.map { "${it.deviceName}: ${it.deviceAddress}" }}")
        if (devlist.deviceList.size > 0 || p2p_discovery_running) {  // don't process empty devicelist which may be the result of a stopped discovery
            conductor.p2p_scanresult = ArrayList<WifiP2pDevice>().also {
                devlist.deviceList.toCollection(it)
            }
        }
    }
}