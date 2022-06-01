package org.nontrivialpursuit.eekhoorn.httphandlers

import org.eclipse.jetty.server.HttpConnection
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.handler.ContextHandler
import org.nontrivialpursuit.eekhoorn.sendErrorText
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class FourOhFour : ContextHandler() {
    // Last resort handler.

    init {
        allowNullPathInfo = true
    }

    override fun doHandle(
            target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
        HttpConnection.getCurrentConnection().httpChannel.response.sendErrorText(Response.SC_NOT_FOUND, "Not found :-/")
        baseRequest.isHandled = true
    }
}