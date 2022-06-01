package org.nontrivialpursuit.eekhoorn.httphandlers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json.Default.encodeToString
import org.eclipse.jetty.server.HttpConnection
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.util.log.Log
import org.eclipse.jetty.util.log.Logger
import org.nontrivialpursuit.eekhoorn.JSON_HTTP_CONTENTTYPE
import org.nontrivialpursuit.eekhoorn.interfaces.HttpEekhoornInterface
import org.nontrivialpursuit.eekhoorn.sendErrorText
import org.nontrivialpursuit.ingeblikt.BundleDescriptor
import org.nontrivialpursuit.ingeblikt.interfaces.RebootMethod
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


@Serializable
data class InjectionResultList(val results: List<InjectionResult>)

@Serializable
data class InjectionResult(val bundle: BundleDescriptor, val success: Boolean)

class AppelflapActionHandler(contextPath: String, val eekhoorn: HttpEekhoornInterface) : ContextHandler() {
    val log: Logger

    init {
        setContextPath(contextPath)
        log = Log.getLogger(this.javaClass)
    }

    override fun doHandle(
            target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
        val base_response: Response = HttpConnection.getCurrentConnection().httpChannel.response
        baseRequest.isHandled = when (request.method to target) {
            "POST" to "/hard-reboot" -> {
                eekhoorn.appelflapBridge.runWithHibernatingGeckoSession(reboot_style = RebootMethod.APP_REBOOT, runnable = {
                    // no-op — normally we'd do some work here taking advantage of exclusive Appelflap access to GeckoView internal on-disk structures
                })
                response.outputStream.close()
                true
            }
            "POST" to "/soft-reboot" -> {
                eekhoorn.appelflapBridge.runWithHibernatingGeckoSession(reboot_style = RebootMethod.ENGINE_REBOOT, runnable = {
                    // no-op — normally we'd do some work here taking advantage of a closed GeckoSession
                })
                response.outputStream.close()
                true
            }
            "POST" to "/launch-wifipicker" -> {
                eekhoorn.appelflapBridge.launchWifiPicker()
                response.outputStream.close()
                true
            }
            "POST" to "/launch-storagemanager" -> {
                if (!eekhoorn.appelflapBridge.launchStorageManager()) {
                    base_response.sendErrorText(Response.SC_GONE)
                }
                response.outputStream.close()
                true
            }
            "POST" to "/inject-caches" -> {
                val results = InjectionResultList(
                    eekhoorn.appelflapBridge.injectAll(honor_injectionlock = false, reboot_style = RebootMethod.ENGINE_REBOOT).map {
                            InjectionResult(it.key, it.value)
                        })
                response.contentType = JSON_HTTP_CONTENTTYPE
                response.outputStream.writer().use { writer ->
                    writer.write(
                        encodeToString(InjectionResultList.serializer(), results)
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