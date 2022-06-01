package org.nontrivialpursuit.appelflap

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import org.nontrivialpursuit.libkitchensink.WifiDigest
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.ByteBuffer

const val UNKNOWN_WIFINET = "<unknown ssid>"
val WLAN_IFACE_REX = Regex("wlan[0-9]+")
val WIFISTATE_REX = Regex("^WIFI_STATE_(.*)$")


val WIFISTATES = WifiManager::class.java.declaredFields.toList().mapNotNull {
    runCatching { it[0] as Int }.getOrNull()
        ?.let { thevalue -> WIFISTATE_REX.matchEntire(it.name)?.groupValues?.getOrNull(1)?.let { thevalue to it } }
}.toMap()


fun wifistateEnumName(enum_val: Int): String {
    return WIFISTATES[enum_val] ?: enum_val.toString()
}


fun Inet4Address.toInt(): Int {
    return ByteBuffer.wrap(this.address.reversedArray()).int
}


fun getAPmodeIPv4Address(context: Context): Inet4Address? {
    // returns null if there are multiple candidates matching the criteria
    val cman = context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val non_ap_netdevnames = cman.allNetworks.mapNotNull {
        cman.getLinkProperties(it).interfaceName
    }.toSet()
    val wlan_ifs = NetworkInterface.getNetworkInterfaces().toList().filter {
        !it.isLoopback && !it.isPointToPoint && !it.isVirtual && it.isUp && it.supportsMulticast() && it.name !in non_ap_netdevnames && it.name.matches(
            WLAN_IFACE_REX
        )
    }
    return wlan_ifs.flatMap { it.inetAddresses.toList() }.filter { it is Inet4Address && it.isSiteLocalAddress }.takeIf { it.size == 1 }
        ?.first() as Inet4Address?
}


fun getWifiDigest(context: Context): WifiDigest {
    val wiman = context.getApplicationContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
    return wiman.connectionInfo.let {

        val in_apmode = runCatching { WifiManager::class.java.getMethod("isWifiApEnabled").invoke(wiman) }.getOrNull()
            ?.let { it as Boolean? }
        val apmode_ip = in_apmode.takeIf { it == true }.let { getAPmodeIPv4Address(context) }?.toInt()

        val ip = it.ipAddress.takeIf { it > 0 } ?: apmode_ip
        WifiDigest(
            when (in_apmode) {
                true -> "AP_MODE"
                else -> wifistateEnumName(wiman.wifiState)
            },
            it.supplicantState.name,
            it.ssid.takeUnless { it == UNKNOWN_WIFINET },
            ip?.let { ip -> "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}" },
            ip,
            WifiManager.calculateSignalLevel(it.rssi, 101)
        )
    }
}
