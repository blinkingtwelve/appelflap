package org.nontrivialpursuit.appelflap.peddlenet.services

import android.net.wifi.p2p.WifiP2pManager
import org.nontrivialpursuit.appelflap.Logger
import org.nontrivialpursuit.appelflap.peddlenet.P2P_ERRORS

class p2pActionListener(val for_class: Class<*>, val topic: String) : WifiP2pManager.ActionListener {

    private val log = Logger(for_class)

    override fun onSuccess() {
        log.v("${topic}: success")
    }

    override fun onFailure(why: Int) {
        log.e("${topic}: failed: ${P2P_ERRORS[why] ?: why}")
    }
}
