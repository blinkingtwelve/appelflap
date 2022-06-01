package org.nontrivialpursuit.eekhoorn.httphandlers

import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.util.log.Log
import org.eclipse.jetty.util.log.Logger
import org.nontrivialpursuit.eekhoorn.interfaces.HttpEekhoornInterface
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class InjectionLockHandler(contextPath: String, val eekhoorn: HttpEekhoornInterface) : ContextHandler() {
    val log: Logger

    init {
        setContextPath(contextPath)
        allowNullPathInfo = true
        log = Log.getLogger(this.javaClass)
    }

    override fun doHandle(
            target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
        if (target != "/") return
        baseRequest.isHandled = when (request.method) {
            "PUT" -> {
                eekhoorn.appelflapBridge.setInjectionLock()
                true
            }
            "DELETE" -> {
                eekhoorn.appelflapBridge.releaseInjectionLock()
                true
            }
            else -> {
                false
            }
        }
    }
}