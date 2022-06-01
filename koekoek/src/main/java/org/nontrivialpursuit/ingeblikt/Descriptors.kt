package org.nontrivialpursuit.ingeblikt

import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.iharder.Base64
import org.nontrivialpursuit.libkitchensink.hexlify
import java.io.File
import java.net.URI
import java.security.SecureRandom
import java.util.*

val BODY_ID_REX = Regex("\\{[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{10}([a-f0-9]{2})\\}")
val STATEHASH_REX = Regex("^[0-9a-f]{8}$")
val GECKO_CHARSET = Charsets.UTF_16LE
const val RECORD_INJECTED = 1
const val RECORD_PUBLISHED = 2

class MissingVersionException(msg: String) : KoekoekException(msg)

fun gecko_encode(thing: String): ByteArray {
    return thing.toByteArray(GECKO_CHARSET)
}

fun body_id_to_path(body_id: String): String {
    return BODY_ID_REX.matchEntire(body_id)?.groups?.get(1)?.let {
        val dirno = Integer.parseInt(it.value, 16)
        return@let pathjoin("${dirno}", "${body_id}.final")
    } ?: throw RuntimeException("Not a suitable body ID: ${body_id}")
}

enum class CacheType(val ns_id: Int) {
    // These need to correspond with the enum values as used in caches.sqlite
    CACHE(0),
    SWORK(1),
}

data class Statehash(val thestate: String) {
    init {
        if (!STATEHASH_REX.matches(thestate)) throw RuntimeException("State hash must match '${STATEHASH_REX.pattern}'")
    }
}

@Serializable
data class BundleIndex(val bundles: List<BundleIndexItem>) {
    fun statehash(): Statehash {
        return Statehash(this.bundles.map {
            it.bundle.fs_identity()
        }.sorted().joinToString("|").toByteArray(JSON_SERIALIZATION_CHARSET).md5().hexlify().substring(0..7))
    }
}

data class PackupTargetDesignation(val type: CacheType, val origin: String, val name: String, val version: Long?) {
    fun identity_parts(): List<String> {
        return listOf(type.name) + listOf(this.origin, this.name).map { it.toByteArray().md5().hexlify() }
    }

    fun fs_identity(): String {
        return this.identity_parts().joinToString(".")
    }

}

@Serializable
data class BundleIndexItem(val bundle: BundleDescriptor, val size: Long, val mtime: Long)

@Serializable
data class BundleDescriptor(val type: CacheType, val origin: String, val name: String, val version: Long) {
    fun identity_parts(): List<String> {
        return listOf(type.name) + listOf(this.origin, this.name).map { it.toByteArray().md5().hexlify() } + listOf(version.toString())
    }

    fun fs_identity(): String {
        return this.identity_parts().joinToString(".")
    }

    fun url_identity(): String {
        return "${this.type.name}/${
            listOf(this.origin, this.name).map {
                Base64.encodeBytes(it.toByteArray(), Base64.URL_SAFE)
            }.joinToString("/")
        }/${this.version}"
    }

    init {
        if (version < 0) {
            throw IllegalArgumentException("Version number must be non-negative")
        }
    }
}

@Serializable
data class Subscriptions(val types: Map<CacheType, SubscriptionOrigins>) {
    fun bump_version(type: CacheType, group: String, name: String, version: Long?, flags: Int): Subscriptions {
        return Subscriptions(types.let {
            it.plus(
                type to (it.get(type) ?: SubscriptionOrigins(HashMap())).bump_version(group, name, version, flags)
            )
        })
    }
}

@Serializable
data class SubscriptionOrigins(
        val groups: Map<String, SubscriptionCache>) {
    fun bump_version(group: String, name: String, version: Long?, flags: Int): SubscriptionOrigins {
        return SubscriptionOrigins(groups.let {
            it.plus(group to (it.get(group) ?: SubscriptionCache(HashMap())).bump_version(name, version, flags))
        })
    }
}

@Serializable
data class SubscriptionCache(
        val names: Map<String, SubscriptionCacheDetails>) {
    fun bump_version(name: String, version: Long?, flags: Int): SubscriptionCache {
        return SubscriptionCache(names.let {
            it.plus(
                name to (it.get(name) ?: SubscriptionCacheDetails(
                    injection_version_min = 0, injection_version_max = 0, injected_version = null, p2p_version_min = 0, p2p_version_max = 0
                )).bump_version(version, flags)
            )
        })
    }
}


@Serializable
data class SubscriptionCacheDetails(
        val injection_version_min: Long,
        val injection_version_max: Long,
        val p2p_version_min: Long,
        val p2p_version_max: Long,
        @Required var injected_version: Long? = null,
) {
    fun bump_version(version: Long?, flags: Int): SubscriptionCacheDetails {
        return SubscriptionCacheDetails(this.injection_version_min,
                                        this.injection_version_max,
                                        this.p2p_version_min.takeIf { ((flags and RECORD_PUBLISHED) == RECORD_PUBLISHED) && (version != null) && (it > version) }
                                            ?.let { version } ?: this.p2p_version_min,
                                        this.p2p_version_max.takeIf { ((flags and RECORD_PUBLISHED) == RECORD_PUBLISHED) && (version != null) && (it < version) }
                                            ?.let { version } ?: this.p2p_version_max,
                                        when (flags and RECORD_INJECTED) {
                                            RECORD_INJECTED -> version
                                            else -> this.injected_version
                                        })
    }
}


data class DBCacheEntry(
        val request_method: String,
        val request_url_no_query: String,
        val request_url_no_query_hash: ByteArray,
        val request_url_query: String,
        val request_url_query_hash: ByteArray,
        val request_referrer: String,
        val request_headers_guard: Int,
        val request_mode: Int,
        val request_credentials: Int,
        val request_contentpolicytype: Int,
        val request_cache: Int,
        val request_body_id: String?,
        val response_type: Int,
        val response_status: Int,
        val response_status_text: String,
        val response_headers_guard: Int,
        val response_body_id: String?,
        val response_security_info_id: Int?,
        val response_principal_info: String,
        val request_redirect: Int,
        val request_referrer_policy: Int,
        val request_integrity: String,
        val request_url_fragment: String,
        val response_padding_size: Int?,
)

@Serializable
data class CacheEntry(
        val request_method: String,
        val request_url_no_query: String,
        val request_url_no_query_hash: ByteArray,
        val request_url_query: String,
        val request_url_query_hash: ByteArray,
        val request_referrer: String,
        val request_headers_guard: Int,
        val request_mode: Int,
        val request_credentials: Int,
        val request_contentpolicytype: Int,
        val request_cache: Int,
        val request_body_id: String?,
        val response_type: Int,
        val response_status: Int,
        val response_status_text: String,
        val response_headers_guard: Int,
        val response_body_id: String?,
        val response_security_info_id: Int?,
        val response_principal_info: String,
        val request_redirect: Int,
        val request_referrer_policy: Int,
        val request_integrity: String,
        val request_url_fragment: String,
        val response_padding_size: Int?,

        val response_url_list: ArrayList<String>,
        val request_headers: List<Pair<String, String>>,
        val response_headers: List<Pair<String, String>>,

        @Required var request_bodysize: Long? = null,
        @Required var response_bodysize: Long? = null,
) {

    fun request_morguepath(basepath: File): File? {
        return request_body_id?.let {
            return@let File(basepath, "${body_id_to_path(it)}")
        }
    }

    fun response_morguepath(basepath: File): File? {
        return response_body_id?.let {
            return@let File(basepath, "${body_id_to_path(it)}")
        }
    }

    fun determine_bodysizes(basepath: File) {
        request_bodysize = request_morguepath(basepath)?.length()
        response_bodysize = response_morguepath(basepath)?.length()
    }
}

@Serializable
data class CacheDescriptor(
        val origin: String,
        val name: String,
        val namespace: CacheType,
        val number_of_entries: Int,
        @Required var user_version: Long? = null,
        @Required val last_server_timestamp: Long?) {

    val version: Long
        get() {
            return user_version ?: last_server_timestamp ?: throw MissingVersionException("No version supplied, and no server response timestamps could be derived to use as surrogate version")
        }
}

@Serializable
data class ServiceworkerDescriptor(
    // meaning of below elements gleaned from ServiceWorkerRegistrar.cpp:ServiceWorkerRegistrar::ReadData().
    // Sidenote: Beware of using the timestamps for comparisons as they're client-clock based (unreliable)
        val version: Int, val stanza: List<String>) {
    fun usercontext(): String {
        return stanza[0]
    }

    fun scope(): URI {
        return URI(stanza[1])
    }

    fun url(): URI {
        return URI(stanza[2])
    }

    fun current_worker_handles_fetch(): Boolean {
        return when (stanza[3]) {
            "true" -> true
            "false" -> false
            else -> throw RuntimeException("Not booleanable: ${stanza[3]}")
        }
    }

    fun regid(): String {
        return stanza[4]
    }

    fun updatemechanism(): Int {
        return Integer.parseInt(stanza[5])
    }

    fun ts_install(): Long {
        return stanza[6].toLong()
    }

    fun ts_activated(): Long {
        return stanza[7].toLong()
    }

    fun ts_updated(): Long {
        return stanza[8].toLong()
    }

    fun isValid(): Boolean {
        if (version != SERVICEWORKER_REGISTRATIONS_FORMAT_VERSION) return false
        if (stanza.size != 9) return false
        try {
            scope()
            url()
            current_worker_handles_fetch()
            updatemechanism()
            ts_install()
            ts_activated()
            ts_updated()
        } catch (e: Exception) {
            return false
        }
        return true
    }

    fun renamed(regid: String): ServiceworkerDescriptor {
        return ServiceworkerDescriptor(version = version, stanza = stanza.toMutableList().also { it[4] = regid })
    }

    fun dump(): String {
        return stanza.joinToString("\n")
    }

    fun md5(): String {
        return dump().toByteArray().md5().hexlify()
    }

    companion object {
        fun fromDump(dump: String): ServiceworkerDescriptor {
            return ServiceworkerDescriptor(
                version = SERVICEWORKER_REGISTRATIONS_FORMAT_VERSION,
                stanza = dump.trimEnd('\n').split(SERVICEWORKER_REGISTRATIONS_FIELDSEPARATOR)
            )
        }
    }
}

@Serializable
data class DumpDescriptor(
        @Required val format: Int = DUMPMETA_VERSION,
        val signatureAlgorithm: String,
        val cacheDescriptor: CacheDescriptor,
        val serviceworkerDescriptor: ServiceworkerDescriptor? = null,
        @Required var nonce: String = "") {

    val name: String = serviceworkerDescriptor?.url()?.toString() ?: cacheDescriptor.name

    fun toBundleDescriptor(): BundleDescriptor {
        return BundleDescriptor(this.serviceworkerDescriptor?.let { CacheType.SWORK } ?: CacheType.CACHE,
                                cacheDescriptor.origin,
                                name,
                                cacheDescriptor.version)
    }

    fun zipfile(bundle_dir: File): File {
        return pathjoin(
            bundle_dir, File(
                pathjoin(
                    "${
                        BundleDescriptor(
                            cacheDescriptor.namespace, cacheDescriptor.origin, name, cacheDescriptor.version
                        ).fs_identity()
                    }", BUNDLE_DUMP_FILENAME
                )
            )
        )
    }

    init {
        if (nonce == "") {
            nonce = ByteArray(128).also {
                SecureRandom().nextBytes(it)
            }.md5().hexlify()
        }
    }
}

@Serializable
data class CacheSecurityInfoMap(val security_map: Map<Int, Pair<ByteArray, ByteArray>>)

data class Cachedir(val dir: File) {
    val morgue = File(dir, MORGUE_DIR_NAME)
    val db = File(dir, CACHE_DB_FILENAME)

    fun bodyfile_for_bodyid(body_id: String): File {
        return File(morgue, body_id_to_path(body_id))
    }
}


@Serializable
data class HeaderFilter(
        val stringmatch_requests: Set<String> = HashSet(),
        val stringmatch_responses: Set<String> = HashSet(),
        val rexmatch_requests: Set<String> = HashSet(),
        val rexmatch_responses: Set<String> = HashSet()) {

    fun enrexify(rexstrs: Set<String>): Set<Regex> {
        return rexstrs.mapNotNull { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() }.toSet()
    }

    @Transient
    val reqfilter = stringmatch_requests.map { it.lowercase() }.toSet() to enrexify(rexmatch_requests)

    @Transient
    val respfilter = stringmatch_responses.map { it.lowercase() }.toSet() to enrexify(rexmatch_responses)


    fun test_requestheader(header: String): Boolean {
        return match_header(header, reqfilter.first, reqfilter.second)
    }

    fun test_responseheader(header: String): Boolean {
        return match_header(header, respfilter.first, respfilter.second)
    }

    fun match_header(header: String, stringfilters: Set<String>, rexfilters: Set<Regex>): Boolean {
        return (header.lowercase() in stringfilters || (rexfilters.firstOrNull { it.matches(header) }?.let { true } ?: false))
    }

    companion object {
        fun sane_default(): HeaderFilter {
            // These headers are usually not great to publish.
            return HeaderFilter(stringmatch_requests = setOf("Authorization", "Proxy-Authorization", "Cookie"), stringmatch_responses = setOf("Set-Cookie"))
        }
    }
}