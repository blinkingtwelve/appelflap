package org.nontrivialpursuit.eekhoorn.httphandlers

import org.eclipse.jetty.server.HttpConnection
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.util.log.Log
import org.eclipse.jetty.util.log.Logger
import org.nontrivialpursuit.eekhoorn.sendErrorText
import org.nontrivialpursuit.ingeblikt.PKIOps.PKIOps
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

const val CHAINLENGTH_HEADERNAME = "X-Appelflap-Chain-Length"

class PKIOpsHandler(contextPath: String, val pkiOps: PKIOps) : ContextHandler() {
    val log: Logger

    init {
        setContextPath(contextPath)
        allowNullPathInfo = true
        log = Log.getLogger(this.javaClass)
    }

    override fun doHandle(
            target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse
    ) {
        val base_response: Response = HttpConnection.getCurrentConnection().httpChannel.response
        if (target != "/") return
        baseRequest.isHandled = when (request.method) {
            "GET" -> {
                val httpout = response.outputStream
                response.contentType = "application/x-pem-file"
                response.characterEncoding = "ascii"
                response.status = Response.SC_OK
                val the_chain = pkiOps.getPemChainForDevCert()
                response.setIntHeader(CHAINLENGTH_HEADERNAME, the_chain.size)
                httpout.write(
                        the_chain.joinToString("\n")
                            .toByteArray(Charsets.US_ASCII)
                )
                httpout.close()
                true
            }
            "DELETE" -> {
                pkiOps.deleteChain()
                response.status = Response.SC_OK
                baseRequest.isHandled = true
                response.outputStream.close()
                true
            }
            "PUT" -> {
                val (success, reason) = pkiOps.ingestSignedAppelflapCertchain(request.inputStream)
                when (success) {
                    true -> {
                        response.status = Response.SC_OK
                    }
                    false -> {
                        base_response.sendErrorText(Response.SC_BAD_REQUEST, reason)
                    }
                }
                true
            }
            else -> {
                false
            }
        }
    }
}