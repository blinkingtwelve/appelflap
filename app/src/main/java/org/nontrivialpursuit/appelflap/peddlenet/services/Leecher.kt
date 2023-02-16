package org.nontrivialpursuit.appelflap.peddlenet.services

import android.os.SystemClock
import android.widget.Toast
import io.github.rybalkinsd.kohttp.dsl.httpGet
import io.github.rybalkinsd.kohttp.ext.asStream
import io.github.rybalkinsd.kohttp.ext.url
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.nontrivialpursuit.appelflap.Logger
import org.nontrivialpursuit.appelflap.R
import org.nontrivialpursuit.appelflap.peddlenet.*
import org.nontrivialpursuit.eekhoorn.PUBLICATIONS_PATH
import org.nontrivialpursuit.ingeblikt.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class Leecher(private val conductor: Conductor) : ServiceHandler {

    override val log = Logger(this)
    override var is_running = false
    val schedxecutor = Executors.newScheduledThreadPool(1)
    var executor: ExecutorService? = null
    var connection_initiator: ScheduledFuture<*>? = null
    val pünktlich_httpclient = OkHttpClient.Builder().connectTimeout(PEER_CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
        .readTimeout(PEER_READ_TIMEOUT, TimeUnit.MILLISECONDS).build()

    fun fetch_bundle(peer: BonjourPeer, bundledesc: BundleDescriptor): Boolean {
        conductor.koekoeksNest.also { kkn ->
            val bundle_url = "${peer.url()}${PUBLICATIONS_PATH}/${bundledesc.url_identity()}"
            val bundle_resp = kotlin.runCatching {
                httpGet(pünktlich_httpclient) {
                    url(bundle_url)
                }
            }.getOrNull() ?: return false
            bundle_resp.use { resp ->
                if (resp.code == 200) {
                    val expected_size = resp.header("Content-Length")?.let { it.toLongOrNull() } ?: return false
                    if (expected_size > MAX_BUNDLE_FETCH_SIZE) {
                        log.w("Bundle size of ${expected_size} deemed to large, url: ${bundle_url}")
                        return false
                    }
                    if (expected_size > kkn.bundle_dir.freeSpace) {
                        log.w("No space to store bundle of size ${expected_size}, url: ${bundle_url}")
                        Toast.makeText(
                            conductor.context, conductor.context.resources.getString(R.string.conductor_leecher_no_space), Toast.LENGTH_LONG
                        ).show()
                        return false
                    }
                    kotlin.runCatching {
                        resp.asStream()?.use {
                            kkn.addBundle(it, expected_size, bundledesc)
                        } ?: throw RuntimeException("No response body")
                    }.exceptionOrNull()?.also {
                        log.w("Indelible bundle ${bundledesc} from ${bundle_url}: ${it.message}") // TODO: disturbance mitigation: blocklist this peer (for distributing faulty bundles)? Blocklist the signer?
                        return false
                    }
                    conductor.leech_p2p_last_download_at = SystemClock.elapsedRealtime()
                    when (peer.is_p2p) {
                        true -> conductor.stats.bytes_leeched_wifip2p += expected_size
                        false -> conductor.stats.bytes_leeched_other += expected_size
                    }
                    return true
                }
            }
            return false
        }
    }

    fun pick_a_peer_and_leech() { // We take a random pick, avoids getting hung up on the same peer all the time (say it's faulty in some way)
        val peer = conductor.get_bonjour_peer_candidates().filter(conductor::filter_for_statehash).randomOrNull() ?: return
        conductor.koekoeksNest.also { kkn ->
            executor?.execute {
                conductor.leech_lock.runWith({
                                                 val their_bundles = kotlin.runCatching {
                                                     httpGet(pünktlich_httpclient) {
                                                         url("${peer.url()}${PUBLICATIONS_PATH}")
                                                     }.use {
                                                         it.body?.let {
                                                             Json.decodeFromString(
                                                                 BundleIndex.serializer(), it.bytes().toString(JSON_SERIALIZATION_CHARSET)
                                                             )
                                                         }
                                                     }
                                                 }.getOrNull()
                                                 their_bundles?.bundles?.shuffled()?.also {
                                                     conductor.stats.bundle_listings_retrieved++ // Also here we take a random pick, avoids getting hung up on the same bundle all the time (say it's faulty in some way)
                                                     val outcomes = acquirables(
                                                         kkn.getSubscriptions(mutex_permits = 0), kkn.listBundles(), it
                                                     ).map {
                                                         fetch_bundle(peer, it)
                                                     }
                                                     if (outcomes.all { it }) { // we retrieved everything we wanted from this peer - add the bundle listing to the skiplist
                                                         their_bundles.also {
                                                             conductor.bundlecollection_skiplist.put(it.statehash(), it)
                                                         }
                                                     }
                                                 }
                                             })
            }
        }
    }

    override fun start(): Boolean {
        is_running = true
        executor = Executors.newSingleThreadExecutor()
        connection_initiator = schedxecutor.scheduleWithFixedDelay(
            { pick_a_peer_and_leech() }, 0L, BONJOUR_RESOLVE_INTERVAL, TimeUnit.SECONDS
        )
        return true
    }

    override fun stop() {
        connection_initiator?.cancel(true)
        executor?.shutdownNow()
    }

    override fun restart() {
        stop()
        start()
    }

}