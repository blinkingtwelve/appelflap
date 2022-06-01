package org.nontrivialpursuit.eekhoorn.httphandlers

import org.eclipse.jetty.server.HttpConnection
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.handler.AbstractHandler
import org.nontrivialpursuit.eekhoorn.sendErrorText
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

enum class HttpVerb(val verb: String) {
    GET("GET"),
    HEAD("HEAD"),
    POST("POST"),
    DELETE("DELETE"),
    PUT("PUT"),
}

class BasicAuthGuard(val authedHeaderValue: String, val free_to_access: List<Pair<HttpVerb, String>>) : AbstractHandler() {
    override fun handle(
            target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
        if (free_to_access.any { target.startsWith(it.second) && request.method == it.first.verb }) return
        val base_response: Response = HttpConnection.getCurrentConnection().httpChannel.response
        val auth_header = request.getHeader("Authorization")
        baseRequest.isHandled = when (auth_header) {
            null -> {
                response.setHeader("WWW-Authenticate", "Basic realm=\"Who's there?\"")
                response.status = Response.SC_UNAUTHORIZED
                true
            }
            authedHeaderValue -> {
                false
            }
            else -> {
                base_response.sendErrorText(Response.SC_FORBIDDEN, "Access denied >:(")
                true
            }
        }
    }
}