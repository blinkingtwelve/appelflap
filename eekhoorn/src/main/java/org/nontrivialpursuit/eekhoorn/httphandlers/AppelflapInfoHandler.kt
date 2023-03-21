package org.nontrivialpursuit.eekhoorn.httphandlers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.util.log.Log
import org.eclipse.jetty.util.log.Logger
import org.nontrivialpursuit.eekhoorn.JSON_HTTP_CONTENTTYPE
import org.nontrivialpursuit.eekhoorn.interfaces.HttpEekhoornInterface
import org.nontrivialpursuit.libkitchensink.BonjourSighting
import org.nontrivialpursuit.libkitchensink.WifiDigest
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Serializable
data class StorageInfo(val disksize: Long, val diskfree: Long)

@Serializable
data class WifiInfo(val network: WifiDigest?)

@Serializable
data class BonjourPeerInfo(val peers: List<BonjourSighting>)


class AppelflapInfoHandler(contextPath: String, val eekhoorn: HttpEekhoornInterface) : ContextHandler() {
    val log: Logger

    init {
        setContextPath(contextPath)
        log = Log.getLogger(this.javaClass)
    }

    override fun doHandle(
            target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
        baseRequest.isHandled = when (request.method to target) {
            "GET" to "/wifi-info" -> {
                response.contentType = JSON_HTTP_CONTENTTYPE
                response.outputStream.writer().use { stream ->
                    stream.write(Json.encodeToString(WifiInfo.serializer(), WifiInfo(eekhoorn.appelflapBridge.getWifiDigest())))
                }
                true
            }
            "GET" to "/bonjour-peers" -> {
                eekhoorn.appelflapBridge.getBonjourPeers().also {
                    response.contentType = JSON_HTTP_CONTENTTYPE
                    response.outputStream.writer().use { stream ->
                        stream.write(Json.encodeToString(BonjourPeerInfo.serializer(), BonjourPeerInfo(it)))
                    }
                }
                true
            }
            "GET" to "/storage-info" -> {
                response.contentType = JSON_HTTP_CONTENTTYPE
                response.outputStream.writer().use { stream ->
                    stream.write(
                        Json.encodeToString(StorageInfo.serializer(), eekhoorn.koekoekBridge.bundle_dir.let {
                            StorageInfo(it.totalSpace, it.usableSpace)
                        })
                    )
                }
                true
            }
            else -> {
                false
            }
        }
    }
}