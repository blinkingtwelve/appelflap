package org.nontrivialpursuit.ingeblikt

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.nontrivialpursuit.ingeblikt.PKIOps.*
import java.io.*
import java.math.BigInteger
import java.security.MessageDigest
import java.security.Signature
import java.security.SignatureException
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipError
import java.util.zip.ZipInputStream

const val BUFSIZE = 4096

open class BundleVerificationException(msg: String) : KoekoekException(msg)

class EntryNameVerificationException(msg: String) : BundleVerificationException(msg)

class BundleVersionException(msg: String) : BundleVerificationException(msg)

class DumpfileUnpacker(instream: InputStream, val pkiOps: PKIOps) {

    companion object {
        fun verifyDump(
                instream: InputStream, pkiOps: PKIOps): Pair<Triple<DumpDescriptor, CacheSecurityInfoMap, Pair<String, BigInteger>>, Int> {
            var entry_count = 0
            val unpacker = DumpfileUnpacker(instream, pkiOps)
            unpacker.processCacheEntries().forEach { entry_count++ }  // Will throw if anything's awry
            return unpacker.descriptors to entry_count
        }
    }

    private val zipstream = ZipInputStream(instream.buffered())
    private val digester = MessageDigest.getInstance(BUNDLE_DIGEST_ALGO)

    private lateinit var verifier: Signature
    val descriptors: Triple<DumpDescriptor, CacheSecurityInfoMap, Pair<String, BigInteger>>
    val dumpdescriptor: DumpDescriptor
        get() {
            return descriptors.first
        }
    val security_info_map: CacheSecurityInfoMap
        get() {
            return descriptors.second
        }
    val certinfo: Pair<String, BigInteger>
        get() {
            return descriptors.third
        }
    private lateinit var current_entry: ZipEntry
    private var current_entry_position = -1L
    private var current_entry_digest: ByteArray? = null
    private var spent = false

    init {
        descriptors = processPreamble()
    }

    enum class BODY_TYPE {
        REQUEST,
        RESPONSE,
    }

    fun chompData(outstream: OutputStream, max_read: Long): MEAL_SIZE {
        val outcome = outstream.slurp(zipstream, max_read, digester)
        current_entry_digest = digester.let {
            val thedigest = digester.digest()
            digester.update(thedigest)  // seed the state back in (as digest() resets) as we want a digest of the whole stream so far.
            return@let thedigest
        }
        return outcome
    }

    fun processCacheEntries(staging_dir: File? = null) = sequence<CacheEntry> {
        if (spent) throw IllegalStateException("readEntries is non-reentrant; initialize a new DumpfileUnpacker instead.")
        val body_ids = HashSet<String>()
        for (entry_number in 0..dumpdescriptor.cacheDescriptor.number_of_entries - 1) {
            val cacheEntry: CacheEntry = advance(
                expected_name = "${entry_number}.json", please_deserialize = CacheEntry.serializer() to MAX_CACHEENTRY_SIZE
            ) as CacheEntry

            verify()

            listOf(
                BODY_TYPE.REQUEST to cacheEntry.request_body_id, BODY_TYPE.RESPONSE to cacheEntry.response_body_id
            ).filter { it.second != null }.forEach { bodyinfo ->

                val (expected_name, body_size) = when (bodyinfo.first) {
                    BODY_TYPE.REQUEST -> "${entry_number}.$REQUEST_BODY_ENTRY_NAME" to cacheEntry.request_bodysize
                    BODY_TYPE.RESPONSE -> "${entry_number}.$RESPONSE_BODY_ENTRY_NAME" to cacheEntry.response_bodysize
                }
                bodyinfo.second?.also { the_body_id ->
                    if (body_ids.contains(the_body_id)) throw BundleVerificationException("Cache entry at position ${current_entry_position}: body ID collision")
                    advance(expected_name = expected_name)
                    val body_out: OutputStream = staging_dir?.let { tempdir ->
                        val fpath = File(tempdir, body_id_to_path(the_body_id))
                        fpath.parentFile.mkdir()
                        fpath.outputStream().buffered()
                    } ?: DevNullStream()
                    body_out.use { ostream ->
                        body_size?.let {
                            if (chompData(ostream, it) != MEAL_SIZE.RIGHT_ON) throw BundleVerificationException(
                                "Mismatched body size at entry ${current_entry_position}"
                            )
                        } ?: throw BundleVerificationException("Cache entry at position ${current_entry_position}: has request/response body ID but no recorded body size")
                    }
                    verify()
                    body_ids.add(the_body_id)
                }
            }
            yield(cacheEntry)
        }
        zipstream.close()
        spent = true
    }

    private fun verify() {
        try {
            zipstream.nextEntry?.also { entry ->
                current_entry_position++
                try {
                    if (!entry.name.endsWith(".sig")) throw EntryNameVerificationException("Unexpected entry name at position ${current_entry_position}.\nExpected extension: .sig\nFound filename: ${entry.name}")
                    val the_sig = ByteArrayOutputStream(MAX_SIG_SIZE).also {
                        it.slurp(zipstream, MAX_SIG_SIZE.toLong(), null)
                            .takeUnless { it == MEAL_SIZE.TOO_MUCH } ?: throw BundleVerificationException("Oversized signature (> ${MAX_SIG_SIZE} b) at position ${current_entry_position}")
                    }.toByteArray()
                    current_entry_digest?.run {
                        verifier.update(this)
                        try {
                            if (!verifier.verify(the_sig)) throw BundleVerificationException("Invalid signature in entry ${entry.name} at position ${current_entry_position}: Signature does not match stream state")
                        } catch (e: SignatureException) {
                            throw BundleVerificationException("Invalid signature in entry ${entry.name} at position ${current_entry_position}: Signature exception: ${e.message}")
                        }
                    }
                } catch (e: IOException) {
                    throw BundleVerificationException("Invalid signature in entry ${entry.name} at position ${current_entry_position}: Can't decode")
                }
            } ?: throw BundleVerificationException("Unexpected end of archive at position ${current_entry_position}")
        } catch (e: ZipError) {
            throw BundleVerificationException("Zip structure broken at position ${current_entry_position}")
        }
    }

    private fun advance(
            expected_name: String? = null, please_deserialize: Pair<KSerializer<*>, Int>? = null): Any? {
        try {
            zipstream.nextEntry?.also {
                current_entry_position++
                current_entry = it
                try {
                    expected_name?.run {
                        if (this != it.name) throw EntryNameVerificationException("Unexpected entry name at position ${current_entry_position}.\nExpected: ${this}\nFound: ${it.name}")
                    }
                } catch (e: IOException) {
                    throw BundleVerificationException("Invalid signature in entry ${it.name} at position ${current_entry_position}: Can't decode")
                }
            } ?: throw BundleVerificationException("Unexpected end of archive at position ${current_entry_position}")
        } catch (e: ZipError) {
            throw BundleVerificationException("Zip structure broken at position ${current_entry_position}")
        }
        return please_deserialize?.let { deserializer_recipe ->
            try {
                return@let ByteArrayOutputStream(deserializer_recipe.second).let {
                    if (chompData(
                            it, deserializer_recipe.second.toLong()
                        ) == MEAL_SIZE.TOO_MUCH) throw BundleVerificationException("Decode buffer (of size ${deserializer_recipe.second}) too small for content at position $current_entry_position")
                    Json.decodeFromString(
                        deserializer_recipe.first, it.toString(
                            JSON_SERIALIZATION_CHARSET_NAME
                        )
                    )
                }
            } catch (e: java.lang.IllegalArgumentException) {
                throw BundleVerificationException("Deserialization error at position $current_entry_position")
            }
        }
    }

    private fun processPreamble(): Triple<DumpDescriptor, CacheSecurityInfoMap, Pair<String, BigInteger>> {
        val signature_cert: X509Certificate = with(advance(expected_name = CERTCHAIN_ENTRY_NAME)) {
            val certchain = ByteArrayOutputStream(MAX_CERTCHAIN_SIZE).let {
                if (chompData(
                        it, MAX_CERTCHAIN_SIZE.toLong()
                    ) == MEAL_SIZE.TOO_MUCH) throw BundleVerificationException("Decode buffer (of size ${MAX_CERTCHAIN_SIZE}) too small for content at position ${current_entry_position}")
                pkiOps.readPemChain(it.toByteArray().inputStream())
            }
            try {
                pkiOps.verifyAppelflapChain(certchain)
            } catch (e: PKIOpsException) {
                throw BundleVerificationException("Signature chain verification failure: ${e.message}")
            }
            return@with certchain.first()
        }
        try {
            advance("$DUMPMETA_VERSION")
            if (chompData(DevNullStream(), 0L) != MEAL_SIZE.RIGHT_ON) throw BundleVerificationException("Unexpected data in version entry")
        } catch (e: EntryNameVerificationException) {
            throw BundleVersionException("This bundle is not compatible with this version of Appelflap")
        }
        val dumpdescriptor: DumpDescriptor = with(
            advance(
                expected_name = META_ENTRY_NAME,
                please_deserialize = DumpDescriptor.serializer() to MAX_DUMPDESCRIPTOR_SIZE,
            )
        ) {
            val local_dumpdescriptor = this!! as DumpDescriptor
            val sig_algo: String = try {
                SignatureAlgorithm.fromValue(local_dumpdescriptor.signatureAlgorithm).algo
            } catch (e: IllegalArgumentException) {
                throw BundleVerificationException("Unrecognized signature algorithm: ${local_dumpdescriptor.signatureAlgorithm}")
            }
            // we now have enough information to instantiate the signature verifier
            verifier = Signature.getInstance(sig_algo).also {
                it.initVerify(signature_cert)
            }
            return@with local_dumpdescriptor
        }
        verify()
        val securitydescriptor: CacheSecurityInfoMap = advance(
            expected_name = SECURITY_INFO_ENTRY_NAME, please_deserialize = CacheSecurityInfoMap.serializer() to MAX_SECURITYDESCRIPTOR_SIZE
        ) as CacheSecurityInfoMap
        verify()
        return Triple(dumpdescriptor, securitydescriptor, signature_cert.subjectDNCN()!! to signature_cert.serialNumber)
    }
}