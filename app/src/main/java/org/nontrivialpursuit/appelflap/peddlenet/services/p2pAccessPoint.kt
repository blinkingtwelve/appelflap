@file:JvmName("p2pAccessPoint")

package org.nontrivialpursuit.appelflap.peddlenet.services

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.EXTRA_WIFI_P2P_GROUP
import org.nontrivialpursuit.appelflap.Logger
import org.nontrivialpursuit.appelflap.peddlenet.Conductor
import org.nontrivialpursuit.appelflap.peddlenet.P2P_ERRORS
import org.nontrivialpursuit.appelflap.peddlenet.ServiceHandler
import org.nontrivialpursuit.appelflap.peddlenet.TENPLUS

class p2pAccessPoint(
        private val conductor: Conductor) : ServiceHandler, BroadcastReceiver() {

    override val log = Logger(this)
    override var is_running = false

    @SuppressLint("NewApi", "MissingPermission")
    override fun start(): Boolean {
        is_running = true
        if (!conductor.p2p_enabled) return false
        conductor.ap_restart_desired = false
        if (!TENPLUS) throw RuntimeException("Requires API 29 / Android 10+")
        // TODO: generate HMAC for the AP name
        // TODO: derive passhprase from HMAC
        conductor.context.registerReceiver(this, IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION))
        val config = WifiP2pConfig.Builder().setNetworkName("DIRECT-${conductor.friendly_nodeID}-${conductor.current_bundlestate}-99AAAA99")
            .setPassphrase("12345678").enablePersistentMode(false).build()
        conductor.p2pmanager.createGroup(conductor.channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                log.v("AP creation request: success")
                is_running = true // TODO: find other measure of success. Set timeout for check on whether p2pdevice is connected & groupinfo mac etc
            }

            override fun onFailure(why: Int) {
                log.v("AP creation request: failed, backgrounded: ${conductor.backgrounded}, reason: ${P2P_ERRORS[why]}")
                //                    is_running = false  // TODO: find other measure of success. Set timeout for check on whether p2pdevice is connected & groupinfo mac etc
            }
        })
        return true
    }

    override fun stop() {
        conductor.p2pmanager.removeGroup(conductor.channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                log.v("Remove p2p group request: success")
            }

            override fun onFailure(why: Int) {
                log.e("Remove p2p group request: fail, reason: ${P2P_ERRORS[why]}")
                //                    conductor.channel.close()
            }

        })
        is_running = false
    }

    override fun restart() {
        stop()
        start()
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                if (intent.getParcelableExtra<WifiP2pGroup>(EXTRA_WIFI_P2P_GROUP)?.isGroupOwner == true) {
                    is_running = true
                }
            }
        }
    }
}