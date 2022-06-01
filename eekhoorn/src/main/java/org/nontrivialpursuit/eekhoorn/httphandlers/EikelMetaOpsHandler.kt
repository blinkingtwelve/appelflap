package org.nontrivialpursuit.eekhoorn.httphandlers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.util.log.Log
import org.eclipse.jetty.util.log.Logger
import org.nontrivialpursuit.eekhoorn.Eikel
import org.nontrivialpursuit.eekhoorn.EikelMeta
import org.nontrivialpursuit.eekhoorn.JSON_HTTP_CONTENTTYPE
import org.nontrivialpursuit.ingeblikt.JSON_SERIALIZATION_CHARSET
import java.io.File
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Serializable
data class EikelStatus(val size: Long, val lastmodified: Long, val meta: EikelMeta)

@Serializable
data class StatusInfo(
        val basedir: String, val disksize: Long, val diskused: Long, val diskfree: Long, val eikels: List<EikelStatus>)


class EikelMetaOpsHandler(contextPath: String, val basedir: File) : ContextHandler() {
    val log: Logger

    init {
        setContextPath(contextPath)
        log = Log.getLogger(this.javaClass)
    }

    override fun doHandle(
            target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
        when (request.method to target) {
            "GET" to "/status" -> {
                handle_status(baseRequest, request, response)
            }
            "POST" to "/nuke" -> {
                basedir.deleteRecursively()
                basedir.mkdir()
                baseRequest.isHandled = true
                response.status = Response.SC_NO_CONTENT
            }
            else -> {
            }
        }
    }

    private fun handle_status(
            baseRequest: Request, @Suppress("UNUSED_PARAMETER") request: HttpServletRequest, response: HttpServletResponse) {
        val eikel_details = LinkedList<EikelStatus>()
        var running_size_total = 0L

        Eikel.get_eikels(basedir).forEach {
            val esize = it.first.body_size()
            running_size_total += esize
            eikel_details.add(
                EikelStatus(
                    size = esize, lastmodified = it.first.body_file.lastModified() / 1000, meta = it.second
                )
            )
        }
        val infoblock = StatusInfo(
            basedir = basedir.absolutePath,
            disksize = basedir.totalSpace,
            diskfree = basedir.freeSpace,
            diskused = running_size_total,
            eikels = eikel_details
        )
        val txbuf = Json.encodeToString(StatusInfo.serializer(), infoblock).toByteArray(JSON_SERIALIZATION_CHARSET)
        response.status = Response.SC_OK
        response.setContentLength(txbuf.size)
        response.contentType = JSON_HTTP_CONTENTTYPE
        response.outputStream.write(txbuf)
        baseRequest.isHandled = true
    }
}