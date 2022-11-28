package org.nontrivialpursuit.eekhoorn.httphandlers

// A debug handler for streaming a serialized cache straight into/from the heart of Geckoview
// and a handler for debug/under-the-hood bits and bobs

import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.util.log.Log
import org.eclipse.jetty.util.log.Logger
import org.nontrivialpursuit.eekhoorn.JSON_HTTP_CONTENTTYPE
import org.nontrivialpursuit.eekhoorn.interfaces.HttpEekhoornInterface
import org.nontrivialpursuit.ingeblikt.*
import org.nontrivialpursuit.ingeblikt.interfaces.RebootMethod
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


class JefferiesTube(
        contextPath: String, val eekhoorn: HttpEekhoornInterface) : ContextHandler() {
    val log: Logger

    init {
        setContextPath(contextPath)
        allowNullPathInfo = true
        log = Log.getLogger(this.javaClass)
    }

    override fun doHandle(
            target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {

        val suspension_style = request.getParameter("suspension")?.let { RebootMethod.valueOf(it) } ?: RebootMethod.NOOP
        val honor_injectionlock = request.getParameter("honor_injectionlock")?.let { it.toBoolean() } ?: true

        baseRequest.isHandled = when (request.method to target) {
            "POST" to "/geckocache" -> {
                eekhoorn.appelflapBridge.inject(
                    request.inputStream, honor_injectionlock = honor_injectionlock, reboot_style = suspension_style
                )
                true
            }
            "GET" to "/geckocache" -> {
                val target = PackupTargetDesignation(
                    CacheType.valueOf(request.getParameter("type")),
                    request.getParameter("origin"),
                    request.getParameter("cache"),
                    request.getParameter("version")?.toLong()
                )
                response.outputStream.use { outstream ->
                    packup(
                        eekhoorn.koekoekBridge.dbOps,
                        eekhoorn.koekoekBridge.profile_dir,
                        outstream,
                        eekhoorn.koekoekBridge.pkiOps,
                        target,
                        HeaderFilter.sane_default()
                    )
                }
                true
            }
            "POST" to "/bundles" -> {
                eekhoorn.koekoekBridge.addBundle(request.inputStream.buffered())
                true
            }
            "POST" to "/garbagecollect" -> {
                eekhoorn.koekoekBridge.garbageCollect()
                true
            }
            "POST" to "/inject-all" -> {
                val results = eekhoorn.appelflapBridge.injectAll(
                    honor_injectionlock = honor_injectionlock, reboot_style = suspension_style
                )
                response.contentType = JSON_HTTP_CONTENTTYPE
                response.outputStream.writer(JSON_SERIALIZATION_CHARSET).use { writer ->
                    results.forEach {
                        writer.write("${it.key}:\t${it.value}")
                    }
                }
                true
            }
            "GET" to "/permits" -> {
                response.outputStream.writer().use { stream ->
                    stream.write("A\tQ\tName\n\n")
                    Lockchest.status().toList().sortedBy { it.first }.sortedBy { it.second.first }.sortedBy { it.second.second }
                        .forEach { stream.write("${it.second.first}\t${it.second.second}\t${it.first}\n") }
                }
                true
            }
            "POST" to "/permits-release" -> {
                response.outputStream.writer().use { stream ->
                    Lockchest.unClog().toList().sortedBy { it.first }.forEach { stream.write("${it.second}\t${it.first}\n") }
                }
                true
            }
            else -> {
                false
            }
        }
    }
}