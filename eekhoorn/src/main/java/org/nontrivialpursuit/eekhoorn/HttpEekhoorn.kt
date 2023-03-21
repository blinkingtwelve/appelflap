package org.nontrivialpursuit.eekhoorn

import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.server.handler.HandlerList
import org.eclipse.jetty.util.log.Log
import org.eclipse.jetty.util.log.Logger
import org.nontrivialpursuit.eekhoorn.httphandlers.*
import org.nontrivialpursuit.eekhoorn.interfaces.HttpEekhoornInterface
import org.nontrivialpursuit.eekhoorn.interfaces.KoekoekBridge
import org.nontrivialpursuit.eekhoorn.interfaces.KoekoekStub
import org.nontrivialpursuit.ingeblikt.AppelflapStub
import org.nontrivialpursuit.ingeblikt.PKIOps.PKIOps
import org.nontrivialpursuit.ingeblikt.interfaces.AppelflapBridge
import java.io.File
import java.util.*

class HttpEekhoorn(
        val eekhoorn_basedir: File,
        portno: Int,
        username_password: String?,
        override var appelflapBridge: AppelflapBridge = AppelflapStub(),
        val pkiOps: PKIOps,
        override val koekoekBridge: KoekoekBridge = KoekoekStub(),
        val DEBUG: Boolean = true) : HttpEekhoornInterface {
    private var jettyserver: Server? = null
    override val credentials: Triple<String, String, String>
    val logger: Logger = Log.getLogger(HttpEekhoorn::class.java.name)
    val eikel_basedir: File
        get() {
            return File(this.eekhoorn_basedir, EIKEL_SUBDIR).also { it.mkdir() }
        }

    fun create_server(portno: Int): Server {
        return Server(portno).also {
            it.handler = HandlerList().also {
                it.handlers = arrayOf<Handler>(
                    HandlerList().also {
                        it.handlers = arrayOf<Handler>(
                            HttpLogger(),
                            CorsWideOpen(),
                            BasicAuthGuard(
                                authedHeaderValue = credentials.third, free_to_access = listOf(
                                    HttpVerb.GET to FAVICON_PATH,
                                    HttpVerb.GET to PUBLICATIONS_PATH,
                                    HttpVerb.GET to GIFT_PATH,
                                    HttpVerb.HEAD to EIKEL_PREFIX,
                                    HttpVerb.GET to EIKEL_PREFIX,
                                    HttpVerb.GET to EIKEL_META_PREFIX,
                                )
                            ),
                        )
                    },
                    ContextHandlerCollection().also {
                        it.handlers = listOf<ContextHandler>(
                            FaviconHandler(FAVICON_PATH),
                            GiftHandler(GIFT_PATH, this),
                            PKIOpsHandler(PKI_OPS_PATH, pkiOps),
                            AppelflapActionHandler(ACTIONS_PREFIX, this),
                            AppelflapInfoHandler(INFO_PREFIX, this),
                            AppelflapSettingsHandler(SETTINGS_PREFIX, this),
                            InjectionLockHandler(INJECTION_LOCK_PATH, this),
                            PublicationHandler(PUBLICATIONS_PATH, this),
                            SubscriptionHandler(SUBSCRIPTIONS_PATH, koekoekBridge),
                            EikelHandler(EIKEL_PREFIX, eikel_basedir),
                            EikelMetaOpsHandler(EIKEL_META_PREFIX, eikel_basedir),
                            AppelflapDownloadsHandler(DOWNLOADS_PREFIX, this),
                        ).let {
                            if (!DEBUG) it else it + listOf(
                                JefferiesTube(
                                    DEBUG_TRICKS_PATH,
                                    this
                                )
                            )
                        }.toTypedArray()
                    },
                    FourOhFour(),
                )
            }
        }
    }

    override fun get_portno(): Int {
        return (jettyserver!!.connectors[0] as ServerConnector).localPort
    }

    override fun shutdown() {
        try {
            jettyserver?.also {
                it.stop()
                it.join()
            }
        } catch (ignored: Exception) {
        } // already stopped perhaps, whatevs
    }

    fun join() {
        jettyserver?.also { it.join() }
    }

    init {
        System.setProperty("org.eclipse.jetty.LEVEL", "INFO")
        credentials = username_password?.let {
            val result = BASIC_AUTH_REX.matchEntire(it) ?: throw RuntimeException("username and password must conform to the following pattern: $BASIC_AUTH_REX.pattern")
            val (username, password) = result.destructured
            return@let Triple(username, password, as_requestheader(username, password))
        } ?: gen_credentials()
        this.eekhoorn_basedir.mkdir()
        while (jettyserver == null) {
            val try_server = create_server(portno)
            try {
                try_server.start()
                jettyserver = try_server // hurray
            } catch (e: Exception) { //  possibly the port is occupied
                if (portno != 0) {
                    logger.warn("Can't start server, possible cause: port $portno is occupied")
                    System.exit(1)
                }
                logger.warn("Server start failed, retrying. Stack trace: ${Arrays.toString(e.stackTrace)}")
            }
        }
    }
}