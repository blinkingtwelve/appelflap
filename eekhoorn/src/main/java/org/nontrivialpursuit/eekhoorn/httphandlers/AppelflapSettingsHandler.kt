package org.nontrivialpursuit.eekhoorn.httphandlers

import org.eclipse.jetty.server.HttpConnection
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.util.log.Log
import org.eclipse.jetty.util.log.Logger
import org.nontrivialpursuit.eekhoorn.interfaces.HttpEekhoornInterface
import org.nontrivialpursuit.eekhoorn.sendErrorText
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class AppelflapSettingsHandler(contextPath: String, val eekhoorn: HttpEekhoornInterface) : ContextHandler() {
    val log: Logger

    init {
        setContextPath(contextPath)
        log = Log.getLogger(this.javaClass)
    }

    override fun doHandle(
            target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
        val base_response: Response = HttpConnection.getCurrentConnection().httpChannel.response
        baseRequest.isHandled = when (request.method to target) {
            "POST" to "/ui-language" -> {
                if (!eekhoorn.appelflapBridge.setLanguage(request.inputStream.reader().readText().trim())) {
                    base_response.sendErrorText(Response.SC_BAD_REQUEST, "This language is not enabled")
                }
                true
            }
            else -> {
                false
            }
        }
    }
}