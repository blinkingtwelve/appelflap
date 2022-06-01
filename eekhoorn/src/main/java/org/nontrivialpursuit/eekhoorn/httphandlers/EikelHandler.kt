package org.nontrivialpursuit.eekhoorn.httphandlers

import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.util.log.Log
import org.eclipse.jetty.util.log.Logger
import org.nontrivialpursuit.eekhoorn.*
import org.nontrivialpursuit.libkitchensink.streamStreamToStream
import java.io.File
import java.io.IOException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


class EikelHandler(contextPath: String, val basedir: File) : ContextHandler() {
    val log: Logger

    init {
        setContextPath(contextPath)
        basedir.mkdir()
    }

    override fun doHandle(
            target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {

        // clean up any leftovers from crashes and aborted uploads
        try {
            Eikel.garbagecollect(basedir)
        } catch (e: IOException) {
            log.warn(e)
        }

        try {
            baseRequest.isHandled = when (request.method) {
                "GET" -> handle_GET(target, request, response)
                "HEAD" -> handle_HEAD(target, request, response)
                "DELETE" -> handle_DELETE(target, request, response)
                "PUT" -> handle_PUT(target, request, response)
                else -> {
                    false
                }
            }
        } catch (e: NoSuchEikelException) {
            log.warn(
                String.format(
                    "File not found. URL: %s File: %s", target, e.message
                )
            )
        }
    }

    private fun handle_DELETE(
            target: String, @Suppress("UNUSED_PARAMETER") request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val eikel = Eikel(basedir, target, EikelMode.R)
        eikel.delete()
        response.status = Response.SC_NO_CONTENT
        response.setContentLength(0)
        return true
    }

    private fun initResponse(
            target: String, @Suppress("UNUSED_PARAMETER") request: HttpServletRequest, response: HttpServletResponse): Eikel {
        val eikel = Eikel(basedir, target, EikelMode.R)
        response.setContentLengthLong(eikel.body_size())
        response.status = Response.SC_OK
        eikel.headers.forEach { entry -> response.setHeader(entry.key, entry.value) }
        if (eikel.headers["Access-Control-Expose-Headers"] == null) {
            response.setHeader("Access-Control-Expose-Headers", eikel.headers.keys.joinToString(", "))
        }
        return eikel
    }

    private fun handle_HEAD(
            target: String, request: HttpServletRequest, response: HttpServletResponse): Boolean {
        initResponse(target, request, response)
        return true
    }

    private fun handle_GET(
            target: String, request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val eikel = initResponse(target, request, response)
        streamStreamToStream(eikel.body_file.inputStream().buffered(), response.outputStream.buffered())
        return true
    }

    private fun handle_PUT(
            target: String, request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val eikel = Eikel(basedir, target, EikelMode.W)
        response.setContentLength(0)
        response.status = Response.SC_NO_CONTENT
        val store_headers = HashMap<String, String>()
        val headernames = request.headerNames
        while (headernames.hasMoreElements()) {
            val headername = headernames.nextElement()
            if (headername.startsWith(HEADER_STORE_PREFIX)) {
                store_headers[headername.substring(HEADER_STORE_PREFIX.length)] = request.getHeader(headername)
            }
        }
        eikel.storeMeta(EikelMeta(target, store_headers))
        streamStreamToStream(request.inputStream.buffered(), eikel.body_file.outputStream().buffered())
        eikel.finalize_upload()
        return true
    }

    init {
        log = Log.getLogger(this.javaClass)
    }
}