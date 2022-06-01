package org.nontrivialpursuit.eekhoorn.httphandlers

import kotlinx.serialization.json.Json
import org.eclipse.jetty.server.HttpConnection
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.util.B64Code
import org.eclipse.jetty.util.log.Log
import org.eclipse.jetty.util.log.Logger
import org.nontrivialpursuit.eekhoorn.HttpEekhoorn
import org.nontrivialpursuit.eekhoorn.JSON_HTTP_CONTENTTYPE
import org.nontrivialpursuit.eekhoorn.sendErrorText
import org.nontrivialpursuit.ingeblikt.*
import org.nontrivialpursuit.ingeblikt.interfaces.PackupContentionStrategy
import org.nontrivialpursuit.ingeblikt.interfaces.RebootMethod
import java.lang.IllegalArgumentException
import java.lang.RuntimeException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


fun parse_path(pathcomponents: List<String>): PackupTargetDesignation {
    if (pathcomponents.size < 3) throw RuntimeException()
    val type = CacheType.valueOf(pathcomponents[0])
    val (origin, cachename) = pathcomponents.slice(1..2).map { B64Code.decode(it, "utf-8") }
    val version = pathcomponents.getOrNull(3).let { versionstring ->
        versionstring?.toLongOrNull()?.also {
            if (it < 0) throw IllegalArgumentException("The version does not look like a number between 0 and 2^63 -1")
        }
    }
    return PackupTargetDesignation(type, origin, cachename, version)
}


class PublicationHandler(contextPath: String, val eekhoorn: HttpEekhoorn) : ContextHandler() {
    val log: Logger

    init {
        setContextPath(contextPath)
        allowNullPathInfo = true
        log = Log.getLogger(this.javaClass)
    }


    override fun doHandle(
            target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
        val base_response: Response = HttpConnection.getCurrentConnection().httpChannel.response
        val pathcomponents = target.substring(1).split('/').filter { it.length > 0 }
        val target = kotlin.runCatching { parse_path(pathcomponents) }.getOrNull()
        val bundle = runCatching { target?.toBundleDescriptor() }.getOrNull()
        baseRequest.isHandled = when (request.method to pathcomponents.size) {

            "PUT" to 3, "PUT" to 4 -> {
                when (eekhoorn.pkiOps.getPemChainForDevCert().size) {
                    3 -> {
                        target?.also {
                            val packupContentionStrategy = request.getParameter("contentionstrategy")
                                ?.let { PackupContentionStrategy.valueOf(it) } ?: PackupContentionStrategy.ENGINE_REBOOT_UPON_CONTENTION
                            when (packupContentionStrategy) {
                                PackupContentionStrategy.NOOP -> {
                                    try {
                                        eekhoorn.koekoekBridge.serializeCache(it)
                                    } catch (e: DanglingBodyFileReferenceException) {
                                        base_response.sendErrorText(Response.SC_SERVICE_UNAVAILABLE, "Aborted due to contention: ${e.message}")
                                    }
                                }
                                PackupContentionStrategy.ENGINE_REBOOT_UPON_CONTENTION -> {
                                    try {
                                        eekhoorn.koekoekBridge.serializeCache(it)
                                    } catch (e: DanglingBodyFileReferenceException) {
                                        eekhoorn.appelflapBridge.runWithHibernatingGeckoSession(RebootMethod.ENGINE_REBOOT) {
                                            eekhoorn.koekoekBridge.serializeCacheRetryOnMissingBodyfile(it)
                                        }
                                    }
                                }
                                PackupContentionStrategy.ENGINE_REBOOT -> {
                                    eekhoorn.appelflapBridge.runWithHibernatingGeckoSession(RebootMethod.ENGINE_REBOOT) {
                                        eekhoorn.koekoekBridge.serializeCacheRetryOnMissingBodyfile(it)
                                    }
                                }
                            }
                        } ?: run {
                            base_response.sendErrorText(Response.SC_BAD_REQUEST, "Invalid path")
                        }
                    }
                    else -> {
                        base_response.sendErrorText(Response.SC_BAD_REQUEST, "Abort: Certificate is unsigned")
                    }
                }
                true
            }

            "DELETE" to 4 -> {
                bundle?.also {
                    response.status = when (eekhoorn.koekoekBridge.deleteBundle(it)) {
                        true -> Response.SC_OK
                        false -> Response.SC_NOT_FOUND
                    }
                } ?: run {
                    base_response.sendErrorText(Response.SC_BAD_REQUEST, "Invalid path")
                }
                true
            }

            "GET" to 4 -> {
                bundle?.also {
                    val dumpfile = eekhoorn.koekoekBridge.getDumpFile(it)
                    when (dumpfile) {
                        null -> {
                            response.status = Response.SC_NOT_FOUND
                            response.outputStream.close()
                            baseRequest.isHandled = true
                        }
                        else -> {
                            response.status = Response.SC_OK
                            response.contentType = "application/zip"
                            dumpfile.inputStream().use { istream ->
                                response.setContentLengthLong(istream.channel.size())
                                response.setHeader(
                                    "Content-Disposition", "attachment; filename=\"${it.fs_identity()}.${BUNDLE_DUMP_FILENAME_EXTENSION}\""
                                )
                                response.outputStream.use {
                                    it.slurp(istream)
                                }
                            }
                        }
                    }
                } ?: run {
                    response.status = Response.SC_BAD_REQUEST
                }
                true
            }

            "GET" to 0 -> {
                val index = BundleIndex(eekhoorn.koekoekBridge.listBundles())
                response.status = Response.SC_OK
                response.contentType = JSON_HTTP_CONTENTTYPE
                response.outputStream.bufferedWriter(JSON_SERIALIZATION_CHARSET).use {
                    it.write(Json.encodeToString(BundleIndex.serializer(), index))
                }
                true
            }
            else -> {
                false
            }
        }
    }
}

fun PackupTargetDesignation.toBundleDescriptor(): BundleDescriptor {
    return BundleDescriptor(this.type, this.origin, this.name, this.version!!)
}
