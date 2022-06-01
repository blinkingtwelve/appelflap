package org.nontrivialpursuit.eekhoorn.httphandlers

import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.util.log.Log
import org.eclipse.jetty.util.log.Logger
import org.nontrivialpursuit.eekhoorn.interfaces.HttpEekhoornInterface
import org.nontrivialpursuit.ingeblikt.slurp
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


class GiftHandler(contextPath: String, val eekhoorn: HttpEekhoornInterface) : ContextHandler() {
    val log: Logger

    init {
        setContextPath(contextPath)
        allowNullPathInfo = true
        log = Log.getLogger(this.javaClass)
    }


    override fun doHandle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
        if (target != "/") return
        baseRequest.isHandled = when (request.method) {
            "GET" -> {
                eekhoorn.appelflapBridge.packageApkInfo?.also { pkginfo ->
                    val (apkfile, apkname, apkversion) = pkginfo
                    response.status = Response.SC_OK
                    response.contentType = "application/vnd.android.package-archive"
                    apkfile.inputStream().use { istream ->
                        response.setContentLengthLong(istream.channel.size())
                        response.setHeader(
                            "Content-Disposition", "attachment; filename=\"${apkname}-${apkversion}.apk\""
                        )
                        response.outputStream.use {
                            it.slurp(istream)
                        }
                    }
                }?.let { true } ?: false
            }
            else -> {
                false
            }
        }
    }
}