package org.nontrivialpursuit.appelflap.peddlenet.services

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import org.nontrivialpursuit.appelflap.BuildConfig
import org.nontrivialpursuit.appelflap.Logger
import org.nontrivialpursuit.appelflap.peddlenet.*

class BonjourLocatee(val conductor: Conductor) : ServiceHandler {
    override val log = Logger(this)
    override var is_running = false

    val nsdmanager: NsdManager by lazy { conductor.context.getSystemService(Context.NSD_SERVICE) as NsdManager }

    private var current_nsd_registration_listener: NsdManager.RegistrationListener? = null

    private fun gen_nsd_registration_listener(): NsdManager.RegistrationListener {
        return object : NsdManager.RegistrationListener {
            override fun onUnregistrationFailed(nsdinfo: NsdServiceInfo, why: Int) {
                log.e("Deregistration failed for servicetype ${nsdinfo.serviceType}, reason: ${NSD_ERRORS[why] ?: why}")
            }

            override fun onServiceUnregistered(p0: NsdServiceInfo?) {
                log.v("service unregistered")
                current_nsd_registration_listener = null
            }

            override fun onRegistrationFailed(nsdinfo: NsdServiceInfo, why: Int) {
                log.e("Registration failed for servicetype ${nsdinfo.serviceType}, reason: ${NSD_ERRORS[why] ?: why}")
                current_nsd_registration_listener = null
            }

            override fun onServiceRegistered(sdinfo: NsdServiceInfo) {
                log.v("service registered: ${sdinfo.serviceName}")
                current_nsd_registration_listener = this
            }
        }
    }

    @Synchronized
    override fun start(): Boolean {
        if (conductor.current_portnumber == null) return false
        is_running = true
        conductor.bonjour_advertiser_restart_desired = false
        val the_portno: Int = conductor.current_portnumber ?: throw IllegalArgumentException("No port number is currently set")
        log.i("start dns service advertisement, servicename: ${conductor.bonjourID}")
        val dnsserviceinfo = NsdServiceInfo().apply {
            serviceName = conductor.bonjourID
            serviceType = BONJOUR_SERVICETYPE
            port = the_portno
            setAttribute(FIELD_STATEHASH, conductor.current_bundlestate.thestate)
            if (!BuildConfig.DEBUG) setAttribute(
                FIELD_APPVERSION,
                BuildConfig.VERSION_CODE.toString()
            )  // APKs signed with the debugging certificate will not be installable by the other end, so don't advertise any
        }
        current_nsd_registration_listener?.also {
            nsdmanager.unregisterService(it)
        }
        nsdmanager.registerService(
            dnsserviceinfo, NsdManager.PROTOCOL_DNS_SD, gen_nsd_registration_listener()
        )
        return true
    }

    @Synchronized
    override fun stop() {
        current_nsd_registration_listener?.also {
            nsdmanager.unregisterService(it)
        }
        is_running = false
    }

    override fun restart() {
        stop()
        start()
    }
}
