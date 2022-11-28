package org.nontrivialpursuit.eekhoorn.httphandlers

import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.util.log.Log
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class HttpLogger : AbstractHandler() {
    val logger = Log.getLogger(HttpLogger::class.java.name)
    override fun handle(
            target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
        logger.info("REQUEST: ${request.method} ${target}")
    }
}