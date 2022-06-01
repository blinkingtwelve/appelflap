package org.nontrivialpursuit.eekhoorn.httphandlers

import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.handler.AbstractHandler
import org.nontrivialpursuit.eekhoorn.QUIESCENCE_HEADER
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

val CORS_ExposedHeaders = setOf("ETag", CHAINLENGTH_HEADERNAME, QUIESCENCE_HEADER)

class CorsWideOpen : AbstractHandler() {
    override fun handle(
            target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
        response.setHeader("Access-Control-Allow-Origin", "*")
        response.setHeader("Access-Control-Expose-Headers", CORS_ExposedHeaders.joinToString(", "))
        if (request.method == "OPTIONS") {
            response.status = Response.SC_NO_CONTENT
            response.setHeader("Access-Control-Allow-Headers", "*")
            response.setHeader("Access-Control-Allow-Methods", "GET, PUT, POST, OPTIONS, DELETE")
            baseRequest.isHandled = true
        }
    }
}