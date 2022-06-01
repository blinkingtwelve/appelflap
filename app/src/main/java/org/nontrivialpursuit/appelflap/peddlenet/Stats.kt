package org.nontrivialpursuit.appelflap.peddlenet

import kotlin.math.round

val KB = 1024 * 1.0

data class Stats(
        var p2p_devices_seen: Int = 0,
        var bonjour_devices_seen: Int = 0,
        var bundle_listings_retrieved: Int = 0,
        var bytes_leeched_wifip2p: Long = 0,
        var bytes_leeched_other: Long = 0,
        var wifip2p_joins_performed: Int = 0,
        var wifip2p_devices_seen: Int = 0) {

    fun asMap(): Map<String, Int> {
        return mapOf(
            "p2p_devices_seen" to p2p_devices_seen,
            "bonjour_devices_seen" to bonjour_devices_seen,
            "bundle_listings_retrieved" to bundle_listings_retrieved,
            "leeched_KB_p2p" to round(bytes_leeched_wifip2p / KB).toInt(),
            "leeched_KB_other" to round(bytes_leeched_other / KB).toInt(),
            "p2p_joins_performed" to wifip2p_joins_performed,
            "total_p2p_devices_seen" to wifip2p_devices_seen,
        )
    }
}
