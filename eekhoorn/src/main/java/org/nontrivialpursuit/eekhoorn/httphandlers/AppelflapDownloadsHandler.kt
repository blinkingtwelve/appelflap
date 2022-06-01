package org.nontrivialpursuit.eekhoorn.httphandlers

import kotlinx.serialization.json.Json
import org.eclipse.jetty.server.HttpConnection
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.util.log.Log
import org.eclipse.jetty.util.log.Logger
import org.nontrivialpursuit.eekhoorn.JSON_HTTP_CONTENTTYPE
import org.nontrivialpursuit.eekhoorn.interfaces.HttpEekhoornInterface
import org.nontrivialpursuit.eekhoorn.sendErrorText
import org.nontrivialpursuit.libkitchensink.DownloadDisplayDescriptorListing
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


val DOWNLOAD_ACTIONS_PATTERN = Regex("^/([0-9a-f]{32})\\.(NOTAG|[0-9a-f]{32})$")

class AppelflapDownloadsHandler(contextPath: String, val eekhoorn: HttpEekhoornInterface) : ContextHandler() {
    val log: Logger

    init {
        setContextPath(contextPath)
        allowNullPathInfo = true
        log = Log.getLogger(this.javaClass)
    }

    override fun doHandle(
            target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
        val base_response: Response = HttpConnection.getCurrentConnection().httpChannel.response
        val action_subject = DOWNLOAD_ACTIONS_PATTERN.matchEntire(target)?.groupValues?.let { it.get(1) to it.get(2) }
        val is_action = (action_subject != null)
        baseRequest.isHandled = when (request.method to target) {
            "GET" to "/" -> {
                response.contentType = JSON_HTTP_CONTENTTYPE
                response.outputStream.writer().use { stream ->
                    stream.write(
                        Json.encodeToString(
                            DownloadDisplayDescriptorListing.serializer(), eekhoorn.appelflapBridge.getDownloadListing()
                        )
                    )
                }
                true
            }
            else -> when (request.method to is_action) {
                "POST" to true -> {
                    when (request.getParameter("action")) {
                        "delete" -> {
                            action_subject?.also {
                                (eekhoorn.appelflapBridge.deleteDownload(it))
                            }
                            true
                        }
                        "open" -> {
                            action_subject?.also {
                                if (!eekhoorn.appelflapBridge.openDownload(it)) base_response.sendErrorText(Response.SC_NOT_FOUND)
                            }
                            true
                        }
                        "share" -> {
                            action_subject?.also {
                                if (!eekhoorn.appelflapBridge.shareDownload(it)) base_response.sendErrorText(Response.SC_NOT_FOUND)
                            }
                            true
                        }
                        else -> false
                    }
                }
                else -> false
            }
        }
    }
}