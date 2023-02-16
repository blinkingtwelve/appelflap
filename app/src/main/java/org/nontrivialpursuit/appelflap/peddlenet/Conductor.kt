package org.nontrivialpursuit.appelflap.peddlenet

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.nsd.NsdServiceInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.PowerManager
import android.os.SystemClock
import org.json.JSONObject
import org.nontrivialpursuit.appelflap.BuildConfig
import org.nontrivialpursuit.appelflap.Logger
import org.nontrivialpursuit.appelflap.peddlenet.Runlevels.DWELL_TIMES
import org.nontrivialpursuit.appelflap.peddlenet.Runlevels.P2P_STATES
import org.nontrivialpursuit.appelflap.peddlenet.Runlevels.levelmap
import org.nontrivialpursuit.appelflap.peddlenet.services.*
import org.nontrivialpursuit.appelflap.get_friendly_nodeID
import org.nontrivialpursuit.appelflap.getLocalBroadcastManager
import org.nontrivialpursuit.eekhoorn.KoekoeksNest
import org.nontrivialpursuit.ingeblikt.*
import java.net.Inet4Address
import java.net.InetAddress
import java.util.*
import java.util.concurrent.*
import kotlin.random.Random

val EMPTY_BUNDLEINDEX_STATEHASH = BundleIndex(LinkedList()).statehash()

data class BonjourPeer(val nodeid: String, val address: InetAddress, val port: Int, val statehash: Statehash, val appversion: Int, val is_p2p: Boolean) {
    fun url(): String {
        return "http://${this.address.hostAddress}:${this.port}"
    }
}

data class APPeer(val nodeid: String, val statehash: Statehash, val scanresult: ScanResult)

class SetStatusReceiver(val conductor: Conductor) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("desired_state")?.also {
            conductor.state_poke(ConductorState.valueOf(it))
        }
    }
}


class Conductor private constructor(
        internal val context: Context, val koekoeksNest: KoekoeksNest) : WifiP2pManager.ChannelListener {

    companion object {

        @SuppressLint("StaticFieldLeak")  // Doesn't leak, the applicationContext is a global singleton
        private var c: Conductor? = null

        @Synchronized
        fun getInstance(context: Context, koekoeksNest: KoekoeksNest): Conductor {
            // Singleton
            return c ?: Conductor(context.applicationContext, koekoeksNest)
        }
    }

    // State bookkeeping
    var started = false
    var ap_restart_desired = false
    var bonjour_advertiser_restart_desired = false
    internal var friendly_nodeID: String = get_friendly_nodeID(context)
    internal val bonjourID = "${BuildConfig.APPLICATION_ID}-${BuildConfig.VERSION_NAME}/${friendly_nodeID}"
    internal var current_p2p_device: WifiP2pDevice? = null
    private var current_networkinfo: NetworkInfo? = null
    internal var current_p2p_network_selector = "nope"
    internal var current_p2pinfo: WifiP2pInfo? = null
        set(value) {
            field = value
            // Hacky way of determining whether an IP address is on the p2p network: string-match it against the first 3 octets of the group owner address, assuming the p2p network is a /24
            current_p2p_network_selector = value?.let {
                it.groupOwnerAddress?.let { addr ->
                    addr.hostAddress?.substringBeforeLast('.') + "."
                }
            } ?: "nope"
        }
    internal var current_groupinfo: WifiP2pGroup? = null
    internal var current_portnumber: Int? = null
        set(value) {
            field = value
            bonjour_advertiser_restart_desired = true
        }
    internal var current_bundlestate: Statehash = koekoeksNest.let {
        BundleIndex(it.listBundles()).statehash()
    }
        set(value) {
            field = value
            bonjour_advertiser_restart_desired = true
        }
    internal var target_state = ConductorState.STOPPED
        @Synchronized set(value) {
            field = value
            target_states_since[value] = SystemClock.elapsedRealtime()
        }
    internal var backgrounded = false
        @Synchronized set(value) {
            field = value
            if (!value && TENPLUS) screenwakelock.acquire()
            log.v("Conductor received backgrounded: ${value}")
        }
    internal val bundlecollection_skiplist = ConcurrentHashMap<Statehash, BundleIndex>()
    internal val target_states_since = mapOf(target_state to SystemClock.elapsedRealtime()).toMap(ConcurrentHashMap())
    internal var p2p_enabled = false
    private val ap_inventory = ConcurrentHashMap<String, APPeer>()
    private var p2p_scan_updated_at = 0L
    private val all_p2p_peers_seen = ConcurrentHashMap<String, Long>()
    private val all_bonjour_peers_seen = ConcurrentHashMap<String, Long>()
    internal var p2p_scanresult = ArrayList<WifiP2pDevice>()
        set(value) {
            field = value
            p2p_scan_updated_at = SystemClock.elapsedRealtime()
            value.map { it.deviceName }.forEach { all_p2p_peers_seen[it] = p2p_scan_updated_at }
            stats.wifip2p_devices_seen = all_p2p_peers_seen.size
        }
    internal var bonjour_scanresult = ConcurrentHashMap<String, NsdServiceInfo>()
    val service_registry = ConcurrentHashMap<String, ServiceHandler>()
    internal val stats = Stats()
    // End state bookkeeping

    val log = Logger(this)
    var schedxecutor: ExecutorService? = null
    val p2pmanager: WifiP2pManager by lazy { context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager }
    val wifimanager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager  // straight context.getSystemService(Context.WIFI_SERVICE) leaks on android < N. Issue id: WifiManagerPotentialLeak
    val lobroman = getLocalBroadcastManager(context).also {
        it.registerReceiver(SetStatusReceiver(this), IntentFilter(FORCE_MODESWITCH_ACTION))
    }
    var multicastlock: WifiManager.MulticastLock = wifimanager.createMulticastLock("conductor:")
    var screenwakelock: PowerManager.WakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
        PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.SCREEN_DIM_WAKE_LOCK, "conductor:"
    )
    val leech_lock = Lockchest.get(this::class.qualifiedName + "_LEECH_LOCK")
    var leechee_p2p_last_upload_at: Long? = null
    var leechee_last_upload_at: Long? = null
    var leech_p2p_last_download_at: Long? = null
    lateinit var channel: WifiP2pManager.Channel

    private val monitors = mapOf<IntentFilter, BroadcastReceiver>(IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    } to object : BroadcastReceiver() {
        // Device change monitor
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val old_status = current_p2p_device?.status
                    current_p2p_device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE) as WifiP2pDevice?
                    if (current_p2p_device!!.status != old_status) {
                        log.d(
                            "device status changed: ${P2P_DEVICE_STATUS[old_status]} -> ${P2P_DEVICE_STATUS[current_p2p_device?.status]}"
                        )
                    }
                }
            }
        }
    }, IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
    } to object : BroadcastReceiver() {
        // p2p enablement monitor
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    when (state) {
                        WifiP2pManager.WIFI_P2P_STATE_ENABLED -> {
                            log.d("p2p enabled")
                            p2p_enabled = true
                            if (target_state !in Runlevels.P2P_STATES && Runlevels.P2P_STATES.intersect(
                                    Runlevels.levelmap.keys
                                ).isNotEmpty()) {
                                target_state = ConductorState.LURKING
                            }
                        }
                        else -> {
                            log.e("p2p disabled")
                            p2p_enabled = false
                            target_state = ConductorState.SEULEMENT_BONJOUR
                        }
                    }
                }
            }
        }
    }, IntentFilter().apply { addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) } to object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val p2pinfo: WifiP2pInfo = intent.getParcelableExtra<WifiP2pInfo>(WifiP2pManager.EXTRA_WIFI_P2P_INFO)!!
                    val networkinfo: NetworkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)!!
                    val groupinfo: WifiP2pGroup? = intent.getParcelableExtra<WifiP2pGroup>(WifiP2pManager.EXTRA_WIFI_P2P_GROUP)
                    // log.d("p2pinfo:\n${p2pinfo}\nnetworkinfo:\n${networkinfo}\ngroupinfo:${groupinfo}")
                    current_p2pinfo = p2pinfo
                    current_groupinfo = groupinfo
                    current_networkinfo = networkinfo
                    bonjour_scanresult.clear()  // networks may have disappeared, old & new networks will be scanned & this will get refilled rapidly
                    log.i("P2P connection changed, networkinfo state: ${networkinfo.detailedState}, group size: ${groupinfo?.clientList?.size ?: 0}")
                }
            }
        }
    })

    fun instantiate_service(servicename: String): ServiceHandler? {
        return when (servicename) {
            APScanner::class.java.simpleName -> APScanner(
                this
            )
            p2pDiscoverer::class.java.simpleName -> p2pDiscoverer(
                this
            )
            BonjourLocatee::class.java.simpleName -> BonjourLocatee(
                this
            )
            BonjourLocator::class.java.simpleName -> BonjourLocator(
                this
            )
            p2pAccessPoint::class.java.simpleName -> p2pAccessPoint(
                this
            )
            p2pClient::class.java.simpleName -> p2pClient(
                this
            )
            Leecher::class.java.simpleName -> Leecher(
                this
            )
            StatusLogger::class.java.simpleName -> StatusLogger(
                this
            )
            StatusBroadcaster::class.java.simpleName -> StatusBroadcaster(
                this
            )
            AppUpdater::class.java.simpleName -> AppUpdater(
                this
            )
            else -> null
        }
    }

    fun have_ongoing_p2p_transfers_in_last_X_seconds(seconds: Int = 10): Boolean {
        return setOf(leech_p2p_last_download_at, leechee_p2p_last_upload_at).filterNotNull().maxOrNull()
            ?.let { ((SystemClock.elapsedRealtime() - it) < (seconds * 1000)) } ?: false
    }

    fun update_ap_inventory(peer: APPeer) {
        ap_inventory[peer.scanresult.BSSID] = peer
        all_p2p_peers_seen[peer.nodeid] = SystemClock.elapsedRealtime()
        stats.p2p_devices_seen = all_p2p_peers_seen.size
    }

    fun update_bonjour_inventory(sinfo: NsdServiceInfo) {
        bonjour_scanresult[sinfo.serviceName] = sinfo
        all_bonjour_peers_seen[sinfo.serviceName.substringAfterLast('/')] = SystemClock.elapsedRealtime()
        stats.bonjour_devices_seen = all_bonjour_peers_seen.size
    }

    @Synchronized
    fun reappreciate_bundlecollection_skiplist() {
        koekoeksNest.also {
            val subs = it.getSubscriptions(mutex_permits = 0)
            val local_bundles = it.listBundles()
            current_bundlestate = BundleIndex(local_bundles).statehash()
            val to_unskip = bundlecollection_skiplist.filterValues {
                acquirables(subs, local_bundles, it.bundles).isNotEmpty()
            }.keys
            bundlecollection_skiplist.minusAssign(to_unskip)
        }
    }

    fun get_bonjour_peer_candidates(): List<BonjourPeer> {
        // peer candidates (to download from), in order of preference
        // if they're on the p2p network, we don't want to connect *through* the group leader with it acting as a relay (horrible performance & amplifies spectrum hogging).
        // but if WE are the group leader, any p2p peer is fine, as then we're the hub of the hub-and-spoke network.
        // the group leader itself is ok, as is any bonjour peer on any non-p2p network.
        if (bonjour_scanresult.size == 0) return ArrayList<BonjourPeer>()  // early return; nothing to do
        val i_am_groupowner: Boolean = current_p2pinfo?.isGroupOwner ?: false
        val groupowner_address: InetAddress = current_p2pinfo?.groupOwnerAddress ?: Inet4Address.getLoopbackAddress()
        return bonjour_scanresult.values.mapNotNull { nsdinfo ->
            val peerstatehash = runCatching {
                nsdinfo.attributes.get(FIELD_STATEHASH)?.decodeToString()?.let { Statehash(it) }
            }.getOrNull()
            val appversion = runCatching {
                nsdinfo.attributes.get(FIELD_APPVERSION)?.decodeToString()?.toIntOrNull()
            }.getOrNull()
            return@mapNotNull appversion?.let { appver ->
                peerstatehash?.let { hash ->
                    nsdinfo.host.hostAddress?.let {
                        BonjourPeer(
                            nsdinfo.serviceName.split('/', limit=2).last(),
                            nsdinfo.host,
                            nsdinfo.port,
                            hash,
                            appver,
                            it.startsWith(current_p2p_network_selector)
                        )
                    }
                }
            }
        }.filter { i_am_groupowner or !it.is_p2p or (it.address == groupowner_address) }.toList()
    }

    fun filter_for_statehash(peer: BonjourPeer): Boolean {
        return peer.statehash.takeUnless { hash ->
            (hash == EMPTY_BUNDLEINDEX_STATEHASH || hash == current_bundlestate || bundlecollection_skiplist.containsKey(
                hash
            ))
        }?.let { true } ?: false
    }

    fun get_p2p_peer_candidates(): List<Pair<String, String>> {
        // peers, appearing in p2p scanresult AND AP scanresult, ordered by signal strength
        val peer_set = p2p_scanresult.map { it.deviceAddress }.toSet()
        return ap_inventory.filter {
            peer_set.contains(it.key) && (!bundlecollection_skiplist.containsKey(it.value.statehash))
        }.values.sortedByDescending { it.scanresult.level }.map { it.scanresult.BSSID to it.scanresult.SSID }
    }


    fun dwell_time_remaining(do_jitter: Boolean = false): Long? {
        val now = SystemClock.elapsedRealtime()
        val dwell_time = DWELL_TIMES[target_state] ?: return null
        val jitter = when (do_jitter) {
            true -> Random.nextDouble(
                0 - DWELL_JITTER_FACTOR * dwell_time, DWELL_JITTER_FACTOR * dwell_time
            ).toInt()  // introduce some randomness to avoid peers falling into a synchronized equilibrium
            false -> 0
        }
        return ((target_states_since[target_state] ?: now) + dwell_time + jitter) - now
    }


    fun get_state(): JSONObject {
        val bonjourpeers = get_bonjour_peer_candidates()
        val now = SystemClock.elapsedRealtime()
        return JSONObject(
            mapOf(
                "timestamp" to System.currentTimeMillis(),
                "nodeid" to friendly_nodeID,
                "p2pname" to current_p2p_device?.deviceName,
                "tcpservice_port_number" to current_portnumber,
                "target_state" to "${this.target_state}",
                "dwell_time_remaining" to ((dwell_time_remaining()?.div(1000))?.toInt()),
                "service_status" to service_registry.toSortedMap().map { it.key to it.value.is_running }.toMap(),
                "ongoing_p2p_transfers" to have_ongoing_p2p_transfers_in_last_X_seconds(),
                "downloading" to (leech_lock.availablePermits() == 0),
                "seconds_since_last_net_upload" to leechee_last_upload_at?.let { (((now - it) / 1000).toInt()) },
                "seconds_since_last_p2p_upload" to leechee_p2p_last_upload_at?.let { (((now - it) / 1000).toInt()) },
                "ap_inventory" to ap_inventory.values.map { it.scanresult.SSID },
                "p2p_scanresult" to p2p_scanresult.map { it.deviceName },
                "p2p_candidate_networks" to get_p2p_peer_candidates().map { it.second },
                "bonjour_peers" to bonjour_scanresult.map { it.key.substringAfterLast('/') },
                "bonjour_candidate_peers_on_p2p" to bonjourpeers.filter { it.is_p2p }.size,
                "bonjour_candidate_peers_on_othernets" to bonjourpeers.filter { !it.is_p2p }.size,
                "bundlestatehash_skiplist" to bundlecollection_skiplist.map { it.key.thestate }.toTypedArray(),
                "my_bundlestate" to current_bundlestate.thestate,
                "my_bundles" to koekoeksNest.let { it.listBundles().map { "${it.bundle.name}@${it.bundle.version}" } },
                "p2p_devicestatus" to "${P2P_DEVICE_STATUS[current_p2p_device?.status] ?: "Unknown"}",
                "p2p_network" to "${current_groupinfo?.networkName}",
                "am_groupowner" to current_groupinfo?.isGroupOwner,
                "networkinfo" to mapOf(
                    "state" to "${current_networkinfo?.detailedState}", "available" to current_networkinfo?.isAvailable
                ),
                "sessionstats" to stats.asMap(),
            )
        )
    }

    fun advise_next_state(): ConductorState? {
        // Determines whether it'd be good to transition to a different state, and if so, which one
        when (target_state in P2P_STATES) {
            false -> return null  // currently not in a transitionable state
            true -> if (!p2p_enabled) return ConductorState.SEULEMENT_BONJOUR  // jump to non-p2p mode
        }

        if (have_ongoing_p2p_transfers_in_last_X_seconds()) return null  // any state change would kill those transfers
        if (dwell_time_remaining(do_jitter = true)!! > 0) return null  // haven't overstaid this state yet
        val am_really_hosting = (target_state == ConductorState.HOSTING && current_groupinfo?.isGroupOwner ?: false)
        if (am_really_hosting && backgrounded) return null // use it or lose it (we can't re-erect it until app is foregrounded again), so don't change
        if (am_really_hosting && get_p2p_peer_candidates().size == 0) return null  // we could switch to joining, but we'd have no network to join to

        // ok we're really going to do something else now, if at all reasonable
        var available_states: MutableSet<ConductorState> = P2P_STATES.filter { levelmap.containsKey(it) }.minus(target_state)
            .minus(ConductorState.LURKING).toMutableSet()
        if (backgrounded) available_states.remove(ConductorState.HOSTING)  // can't start AP when backgrounded/screenlocked
        if (get_p2p_peer_candidates().size > 0) available_states.remove(ConductorState.HOSTING)  // let's not host if we have candidate peers in sight
        if (available_states.size > 0) return available_states.random()
        return null
    }


    fun state_poke(desired_state: ConductorState? = null) {
        Lockchest.runWith(this::class.qualifiedName + "_TRANSITION_LOCK", {
            log.v("state poked")
            if (desired_state != null && desired_state in levelmap) {
                log.v("state change: current target state: ${target_state}, setting requested: ${desired_state}")
                target_state = desired_state
            } else {
                val new_state = advise_next_state()
                if (new_state != null) {
                    log.v("state change: current target state: ${target_state}, setting advised: ${new_state}")
                    target_state = new_state
                }
            }
            val these_should_run: Set<String> = levelmap[target_state]!!
            // stop and remove the unwanted
            service_registry.filterNot { these_should_run.contains(it.key) }.toList().forEach {
                it.second.stop()
                service_registry.remove(it.first)
            }
            // check up on / start & add the wanted
            these_should_run.forEach {
                val the_svc: ServiceHandler? = service_registry[it]
                if (the_svc == null) {
                    service_registry[it] = instantiate_service(it)!!
                } else {
                    if (!the_svc.is_running) {
                        the_svc.restart()
                    }
                }
            }
            // opportunistically restart AP (if there is one) to communicate updated state hash, only if it can't hurt
            service_registry[p2pAccessPoint::class.java.simpleName]?.also {
                if (it.is_running && ap_restart_desired && !have_ongoing_p2p_transfers_in_last_X_seconds()) {
                    it.restart()
                }
            }
            service_registry[BonjourLocatee::class.java.simpleName]?.also {
                if (it.is_running && bonjour_advertiser_restart_desired) {
                    it.restart()
                }
            }
        })
    }


    fun start(portno: Int) {
        started = true
        log.i("Starting conductor on node ${friendly_nodeID}")
        current_portnumber = portno
        log.i("Port number: ${current_portnumber}")
        monitors.forEach { el -> context.registerReceiver(el.value, el.key) }
        multicastlock.acquire()
        if (TENPLUS) {
            channel = p2pmanager.initialize(context, context.mainLooper, this)
            screenwakelock.acquire()
            target_state = ConductorState.LURKING
        } else {
            target_state = ConductorState.SEULEMENT_BONJOUR
        }
        schedxecutor = Executors.newScheduledThreadPool(1).also {
            it.scheduleAtFixedRate(
                { state_poke() }, 0, CONDUCTOR_POKE_INTERVAL, TimeUnit.SECONDS
            )
        }
        koekoeksNest.CollectionUpdateCallbacks.set(this::class.java.simpleName, { this.reappreciate_bundlecollection_skiplist() })
        koekoeksNest.SubscriptionUpdateCallbacks.set(this::class.java.simpleName, { this.reappreciate_bundlecollection_skiplist() })
    }


    @Synchronized
    fun stop() {
        started = false
        koekoeksNest.CollectionUpdateCallbacks.remove(this::class.java.simpleName)
        koekoeksNest.SubscriptionUpdateCallbacks.remove(this::class.java.simpleName)
        // teardown, in reverse order
        schedxecutor?.shutdownNow()
        monitors.values.toList().reversed().forEach {
            try {
                context.unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {  // not regged
            }
        }
        //service_registry.values.toList().reversed().forEach { it.stop() }
        state_poke(ConductorState.STOPPED)
        multicastlock.also {
            try {
                if (it.isHeld) it.release()
            } catch (e: RuntimeException) {
            }  // wasn't locked
        }
        screenwakelock.also {
            try {
                if (it.isHeld) it.release()
            } catch (e: RuntimeException) {
            }  // wasn't locked
        }
        if (TENPLUS) channel.close()
    }


    override fun onChannelDisconnected() {
        if (started) {
            log.e("Channel disconnected, reinitializing")
            channel = p2pmanager.initialize(context, context.mainLooper, this)
        }
    }
}
