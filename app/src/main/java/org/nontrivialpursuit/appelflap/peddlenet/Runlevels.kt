package org.nontrivialpursuit.appelflap.peddlenet

import org.nontrivialpursuit.appelflap.BuildConfig
import org.nontrivialpursuit.appelflap.peddlenet.services.*
import java.util.*

object Runlevels {
    val base_set = setOf(
        StatusLogger::class.java.simpleName, StatusBroadcaster::class.java.simpleName
    )
    val p2p_client_set: Set<String> = setOf(
        APScanner::class.java.simpleName, p2pDiscoverer::class.java.simpleName
    )
    val just_bonjour: Set<String> = base_set + setOf(
        BonjourLocator::class.java.simpleName,
        BonjourLocatee::class.java.simpleName,
        Leecher::class.java.simpleName,
        AppUpdater::class.java.simpleName,
    )
    val lurk_set = just_bonjour + p2p_client_set
    val join_set = lurk_set.plus(p2pClient::class.java.simpleName)
    val hosting_set = lurk_set.plus(p2pAccessPoint::class.java.simpleName)

    // API < 29 (Android <10) can't erect APs with names & passwords of our choosing, and can't make faux-wifi-P2P connections to such APs either
    val levelmap: HashMap<ConductorState, Set<String>> = if (BuildConfig.WIFI_P2P_ENABLED && TENPLUS) {
        hashMapOf(
            ConductorState.STOPPED to base_set,
            ConductorState.SEULEMENT_BONJOUR to just_bonjour,
            ConductorState.LURKING to lurk_set,
            ConductorState.JOINING to join_set,
            ConductorState.HOSTING to hosting_set
        )
    } else {
        hashMapOf(
            ConductorState.STOPPED to base_set, ConductorState.SEULEMENT_BONJOUR to just_bonjour,
        )
    }

    val DWELL_TIMES = mapOf(
        // in millis
        ConductorState.HOSTING to 360_000, ConductorState.JOINING to 180_000, ConductorState.LURKING to 10_000
    )

    val P2P_STATES = setOf(
        ConductorState.LURKING, ConductorState.HOSTING, ConductorState.JOINING
    )
}