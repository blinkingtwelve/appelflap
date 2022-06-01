package org.nontrivialpursuit.appelflap.peddlenet.services

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import org.nontrivialpursuit.appelflap.Logger
import org.nontrivialpursuit.appelflap.peddlenet.*
import org.nontrivialpursuit.ingeblikt.Statehash
import java.util.concurrent.*

class BonjourLocator(val conductor: Conductor) : ServiceHandler {
    override val log = Logger(this)
    override var is_running = false

    val nsdmanager: NsdManager by lazy { conductor.context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    val schedxecutor = Executors.newScheduledThreadPool(1)
    var resolvers: ExecutorService? = null
    var statehash_scanner: ScheduledFuture<*>? = null

    private var current_nsd_discovery_listener: NsdManager.DiscoveryListener? = null
    private val discovered_services = ConcurrentHashMap<String, NsdServiceInfo>()

    private fun statehash_scan() {
        discovered_services.values.toList().forEach {
            resolvers?.submit {
                nsdmanager.resolveService(it, gen_nsd_resolve_listener())
            }
        }
    }


    private fun gen_nsd_resolve_listener(): NsdManager.ResolveListener {
        return object : NsdManager.ResolveListener {
            override fun onResolveFailed(sinfo: NsdServiceInfo, why: Int) {
                log.w("resolving failed for ${sinfo}, error: ${NSD_ERRORS[why] ?: why}")
                discovered_services.remove(sinfo.serviceName)
                conductor.bonjour_scanresult.remove(sinfo.serviceName)
            }

            override fun onServiceResolved(sinfo: NsdServiceInfo) {
                val their_statehash = sinfo.attributes.get(FIELD_STATEHASH)?.decodeToString()?.let {
                    kotlin.runCatching { Statehash(it) }.getOrNull()
                }
                their_statehash?.also {
                    log.v("resolving succeeded for ${sinfo.serviceType}: ${sinfo.serviceName} @ ${sinfo.host}:${sinfo.port} / ${their_statehash}")
                    conductor.update_bonjour_inventory(sinfo)
                }
            }
        }
    }


    private fun gen_nsd_discovery_listener(): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {

            override fun onServiceFound(sinfo: NsdServiceInfo) {
                if (sinfo.serviceName != "${conductor.bonjourID}" || sinfo.host != null) {  // we may have found ourselves
                    log.d("found a '${sinfo.serviceType}' at '${sinfo.serviceName}'")
                    discovered_services.put(sinfo.serviceName, sinfo)
                }
            }

            override fun onStopDiscoveryFailed(servicetype: String, why: Int) {
                log.e("Discovery start failed for servicetype ${servicetype}, reason: ${NSD_ERRORS[why] ?: why}")
            }

            override fun onStartDiscoveryFailed(servicetype: String, why: Int) {
                log.e("Discovery start failed for servicetype ${servicetype}, reason: ${NSD_ERRORS[why] ?: why}")
            }

            override fun onDiscoveryStarted(servicetype: String) {
                current_nsd_discovery_listener = this
                log.d("discovery started for ${servicetype}")
            }

            override fun onDiscoveryStopped(servicetype: String) {
                current_nsd_discovery_listener = null
                log.d("discovery stopped for ${servicetype}")
                log.w("discovery stopped behind our back, restarting")
                start()
            }

            override fun onServiceLost(serviceinfo: NsdServiceInfo) {
                log.d("service lost: ${serviceinfo.serviceName} at ${serviceinfo.host}:${serviceinfo.port}")
                // We could remove the service from discovered_services and the conductor.bonjour_scanresult here.
                // But some versions of Android report service loss too eagerly, leading to flipflopping.
                // The failure of a direct unicast resolving attempt as caught in the ResolveListener is a much more
                // reliable indicator of peer departure.
            }
        }
    }


    @Synchronized
    override fun start(): Boolean {
        is_running = true
        current_nsd_discovery_listener?.also {
            nsdmanager.stopServiceDiscovery(it)  // free current listener, if any
        }
        nsdmanager.discoverServices(
            BONJOUR_SERVICETYPE, NsdManager.PROTOCOL_DNS_SD, gen_nsd_discovery_listener()
        )
        resolvers = Executors.newCachedThreadPool()
        statehash_scanner = schedxecutor.scheduleWithFixedDelay({ statehash_scan() }, 5, BONJOUR_RESOLVE_INTERVAL, TimeUnit.SECONDS)
        return true
    }


    @Synchronized
    override fun stop() {
        current_nsd_discovery_listener?.also {
            nsdmanager.stopServiceDiscovery(it)  // free current listener, if any
        }
        statehash_scanner?.cancel(true)
        resolvers?.also {
            it.shutdownNow()
            it.awaitTermination(10, TimeUnit.SECONDS)
        }
        discovered_services.clear()
        is_running = false
    }

    override fun restart() {
        stop()
        start()
    }
}

