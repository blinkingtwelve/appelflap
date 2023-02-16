@file:JvmName("p2pClient")
@file:Suppress("DEPRECATION")  // TODO: Move to ConnectivityManager#getLinkProperties() instead of using NetworkInfo

package org.nontrivialpursuit.appelflap.peddlenet.services

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.MacAddress
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice.CONNECTED
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import org.nontrivialpursuit.appelflap.Logger
import org.nontrivialpursuit.appelflap.peddlenet.Conductor
import org.nontrivialpursuit.appelflap.peddlenet.P2P_ERRORS
import org.nontrivialpursuit.appelflap.peddlenet.ServiceHandler

class p2pClient(
        private val conductor: Conductor) : ServiceHandler, BroadcastReceiver() {

    override val log = Logger(this)
    override var is_running = false

    val errorcnt = HashMap<Int, Int>()

    val peer_blacklist = HashMap<String, Long>()  // TODO: track failed connects, and at which time

    @SuppressLint("NewApi")
    fun make_p2p_config(peerinfo: Pair<String, String>): WifiP2pConfig {
        when (Build.VERSION.SDK_INT < 29) {

            true -> return WifiP2pConfig().also {
                it.deviceAddress = peerinfo.first
                it.groupOwnerIntent = 0  // == GROUP_OWNER_INTENT_MIN in api-30
            }
            else -> return WifiP2pConfig.Builder().setDeviceAddress(MacAddress.fromString(peerinfo.first)).setPassphrase("12345678")
                .setNetworkName(peerinfo.second).build()
        }
    }

    @SuppressLint("MissingPermission")  // should be checked by embedding app, no UI code in this library
    override fun start(): Boolean {
        if (!conductor.p2p_enabled) return false
        val peerinfo = conductor.get_p2p_peer_candidates().firstOrNull() ?: return false
        is_running = true
        conductor.context.registerReceiver(
            this, IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        )
        conductor.p2pmanager.connect(conductor.channel, make_p2p_config(peerinfo), object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                log.d("Connect call: success")
                log.i("CONNECTED TO ${peerinfo.second}")
            }

            override fun onFailure(why: Int) {
                log.e("Connect call: failed with error: ${P2P_ERRORS[why]}")
                errorcnt[why] = (errorcnt.get(why) ?: 0) + 1
                log.e("${errorcnt.map { (P2P_ERRORS[it.key] ?: it.key.toString()) to it.value }.toMap()}")
                is_running = false
            }
        })
        return true
    }


    override fun stop() {
        try {
            conductor.context.unregisterReceiver(this)
        } catch (e: RuntimeException) {
            // pass, wasn't regged
        }
        if (conductor.current_p2p_device?.status == CONNECTED) {
            conductor.channel.close()  // The nuclear option to avoid a "do you want to disconnect" system popup
        } else {
            conductor.p2pmanager.cancelConnect(
                conductor.channel, p2pActionListener(
                    this.javaClass, "P2P cancel connect call"
                )
            )
        }
        is_running = false
    }

    override fun restart() {
        stop()
        start()
    }


    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val p2pinfo: WifiP2pInfo = intent.getParcelableExtra<WifiP2pInfo>(WifiP2pManager.EXTRA_WIFI_P2P_INFO)!!
                val networkinfo: NetworkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)!!
                val groupinfo: WifiP2pGroup? = intent.getParcelableExtra<WifiP2pGroup>(WifiP2pManager.EXTRA_WIFI_P2P_GROUP)
                log.d("Connection changed event: p2pinfo:\n${p2pinfo}\nnetworkinfo:\n${networkinfo}\ngroupinfo:${groupinfo}")
                p2pinfo.also {
                    if (it.groupFormed && !it.isGroupOwner) conductor.stats.wifip2p_joins_performed += 1
                }
                if (networkinfo.state == NetworkInfo.State.DISCONNECTED && networkinfo.isAvailable && errorcnt.values.sum() >= 5) {
                    log.e("TIRED OF FAILURES: ${errorcnt.map { (P2P_ERRORS[it.key] ?: it.key.toString()) to it.value }
                        .toMap()}, REBOOTING P2P STACK")
                    conductor.channel.close()
                    errorcnt.clear()
                    is_running = false
                }
            }
        }
    }
}

