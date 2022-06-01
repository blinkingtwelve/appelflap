package org.nontrivialpursuit.libkitchensink

import kotlinx.serialization.Serializable

@Serializable
data class WifiDigest(val wifistate: String, val supplicantstate: String, val ssid: String?, val ipaddress: String?, val ipaddress_raw: Int?, val strength: Int)

@Serializable
data class BonjourSighting(val id: Long, val friendly_id: String)

@Serializable
data class DownloadDisplayDescriptorListing(val downloads: List<DownloadDisplayDescriptor>)

@Serializable
data class DownloadDisplayDescriptor(
        val url: String, val filename: String, val size: Long, val mtime: Long, val mimetype: String?, val ID: String)