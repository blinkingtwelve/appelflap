package org.nontrivialpursuit.eekhoorn.httphandlers

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.eclipse.jetty.server.HttpConnection
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.util.log.Log
import org.eclipse.jetty.util.log.Logger
import org.nontrivialpursuit.eekhoorn.ConcurrentEditException
import org.nontrivialpursuit.eekhoorn.JSON_HTTP_CONTENTTYPE
import org.nontrivialpursuit.eekhoorn.interfaces.KoekoekBridge
import org.nontrivialpursuit.ingeblikt.*
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.Request
import org.nontrivialpursuit.eekhoorn.QUIESCENCE_HEADER
import org.nontrivialpursuit.eekhoorn.sendErrorText
import org.nontrivialpursuit.libkitchensink.hexlify
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

val EDITCONFLICT_ADVICE = """Only conditional requests (with If-Match header) are allowed for PUT, and the wildcard value ("*") is disallowed.
                            | The webapp's strategy for writing to this shared repository while preventing overwriting any concurrently made edits should be as follows:
                            | 
                            | 1. Disavow any knowledge of the current state of the subscriptions.
                            | 2. Acquire the current state of the subscriptions object by GETing it, and denote the response's `ETag` header value.
                            | 3. Apply the desired mutations, taking into account that the subscriptions may not be in the same state the webapp last left them in (for instance, Appelflap may have updated an `injected_version` field of one or more caches).
                            | 4. Copy the `ETag` header value from step 2 into the `If-Match` request header.
                            | 5. Fire off the PUT-request.
                            | 6. If the response indicates success, the new subscription set has been committed. If the response indicates a 412 Precondition Failed error, something has happened to the subscriptions concurrently between step 2 and 5. The webapp should go to step 1, and re-evaluate whether the intended mutations are still desired, and if so, apply (some of) them on top of the updated state as received from Appelflap.
                        """.trimMargin()


class SubscriptionHandler(contextPath: String, val koekoekBridge: KoekoekBridge) : ContextHandler() {
    val log: Logger
    val subser = Subscriptions.serializer()
    val WILDCARD_ETAG = etag("*")

    init {
        setContextPath(contextPath)
        log = Log.getLogger(this.javaClass)
        allowNullPathInfo = true
    }

    fun etag(thething: String): String {
        return "\"${thething}\""
    }

    override fun doHandle(
            target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
        val base_response: Response = HttpConnection.getCurrentConnection().httpChannel.response
        baseRequest.isHandled = when (request.method to target) {
            "GET" to "/" -> {
                val subs_bin = Json.encodeToString(subser, koekoekBridge.getSubscriptions()).toByteArray()
                response.contentType = JSON_HTTP_CONTENTTYPE
                response.setHeader("ETag", etag(subs_bin.md5().hexlify()))
                response.outputStream.use {
                    it.write(subs_bin)
                }
                true
            }
            "PUT" to "/" -> {
                val ifmatch: String? = request.getHeader("If-Match")
                if (ifmatch == null || ifmatch == WILDCARD_ETAG) {
                    base_response.sendErrorText(
                        Response.SC_BAD_REQUEST, EDITCONFLICT_ADVICE
                    )
                } else {
                    try {
                        val subscription = Json.decodeFromString(subser, request.inputStream.reader(JSON_SERIALIZATION_CHARSET).readText())
                        val subscriptions_bytes = Json.encodeToString(subser, subscription).toByteArray(JSON_SERIALIZATION_CHARSET)
                        if (koekoekBridge.saveSubscriptions(
                                subscriptions_bytes, previous_version_hash = ifmatch.trim('"')
                            )) {
                            response.contentType = JSON_HTTP_CONTENTTYPE
                            response.setHeader("ETag", etag(subscriptions_bytes.md5().hexlify()))
                            response.outputStream.use {
                                it.write(subscriptions_bytes)
                            }
                        } else {
                            base_response.sendErrorText(
                                Response.SC_INTERNAL_SERVER_ERROR, "Saving subscriptions failed: Unexpected rename() failure."
                            )
                        }
                    } catch (e: ConcurrentEditException) {
                        base_response.sendErrorText(
                            Response.SC_PRECONDITION_FAILED, EDITCONFLICT_ADVICE
                        )
                    } catch (e: SerializationException) {
                        base_response.sendErrorText(Response.SC_BAD_REQUEST, e.message)
                    }
                }
                koekoekBridge.garbageCollect()
                true
            }
            "GET" to "/injectables" -> {
                val bundles = koekoekBridge.listBundles()
                val injectable_bundles = BundleIndex(bundles.filter {
                    it.bundle in injectables(
                        koekoekBridge.getSubscriptions(mutex_permits = 0), bundles
                    )
                })
                response.setHeader(QUIESCENCE_HEADER, koekoekBridge.quiet_time().toString())
                response.contentType = JSON_HTTP_CONTENTTYPE
                response.outputStream.writer().use {
                    it.write(Json.encodeToString(BundleIndex.serializer(), injectable_bundles))
                }
                true
            }
            else -> {
                false
            }
        }
    }
}
