@file:JvmName("Constants")

package org.nontrivialpursuit.appelflap.peddlenet

import android.net.nsd.NsdManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import org.nontrivialpursuit.appelflap.BuildConfig

enum class ConductorState {
    STOPPED,
    SEULEMENT_BONJOUR,
    LURKING,
    JOINING,
    HOSTING
}

val BONJOUR_SERVICETYPE = "_${BuildConfig.APPLICATION_ID.replace('.', '-')}._tcp."
const val FIELD_STATEHASH = "statehash"
const val FIELD_APPVERSION = "appver"

const val NOTIFICATION_STATUS_CHANNEL = "statuschannel"
const val STATUS_PUSH_ACTION = "CONDUCTOR_STATUS"
const val STATUS_INTERVAL_ACTION = "CONDUCTOR_STATUS_INTERVAL"
const val FORCE_MODESWITCH_ACTION = "FORCE_MODESWITCH"
const val BACKGROUNDED_ACTION = "BACKGROUNDED_ACTION"

val PEER_SSID_REX = Regex("^DIRECT-(?<nodeid>[2-9a-hjkmnp-zA-HJKLMNP-Z]{4})-(?<statehash>[0-9a-f]{8})-(?<hmac>[0-9a-f]{8})$")

const val LOG_DIR = "logs"  // path inside cache dir, needs to match xml res
const val LOG_FILE_EXTENSION = ".log.gz"

const val APKUPGRADE_DIR = "apk_upgrades"  // path inside cache dir, needs to match xml res

// in seconds, rumour has it that it's limited to 4 times in 2 minutes in Android 8 in foreground, once every 30 minutes in the background.
// See https://www.xda-developers.com/android-pie-throttling-wi-fi-scans-crippling-apps/ .
// In some future release manual scan triggering is going to be disabled alltogether.
const val APSCAN_POLL_INTERVAL = 31L

// main check-and-poke loop
const val CONDUCTOR_POKE_INTERVAL = 11L

// Bonjour polling interval (to keep bonjour peerlist up to date, including unicast requests to get TXT records with port and (possibly updated) state hashes
const val BONJOUR_RESOLVE_INTERVAL = 7L
const val DWELL_JITTER_FACTOR = 0.33

val P2P_DEVICE_STATUS = mapOf(
    WifiP2pDevice.AVAILABLE to "Available",
    WifiP2pDevice.CONNECTED to "Connected",
    WifiP2pDevice.FAILED to "Failed",
    WifiP2pDevice.INVITED to "Invited",
    WifiP2pDevice.UNAVAILABLE to "Unavailable"
)

val NSD_ERRORS = mapOf(
    NsdManager.FAILURE_ALREADY_ACTIVE to "Already active",
    NsdManager.FAILURE_INTERNAL_ERROR to "Internal error",
    NsdManager.FAILURE_MAX_LIMIT to "Limit reached"
)

val P2P_ERRORS = mapOf(
    WifiP2pManager.P2P_UNSUPPORTED to "p2p unsupported", WifiP2pManager.BUSY to "busy", WifiP2pManager.ERROR to "unspecified error"
)

val TENPLUS = Build.VERSION.SDK_INT >= 29

const val MAX_BUNDLE_FETCH_SIZE = 256 * 1024 * 1024  // We consider bundles larger than this ridiculous
const val MAX_APK_FETCH_SIZE = 256 * 1024 * 1024  // We consider APKs larger than this ridiculous
const val PEER_CONNECT_TIMEOUT = 3_000L
const val PEER_READ_TIMEOUT = 5_000L

const val SERVICE_START_DELAY_WARN_MS = 1000L
