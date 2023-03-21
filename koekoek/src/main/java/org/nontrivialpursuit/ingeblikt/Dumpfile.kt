package org.nontrivialpursuit.ingeblikt

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nontrivialpursuit.ingeblikt.PKIOps.PKIOps
import org.nontrivialpursuit.ingeblikt.interfaces.CacheDBOps
import java.io.*
import java.math.BigInteger
import java.net.URI
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.security.Signature
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ServiceworkerSerdeException(msg: String) : KoekoekException(msg)
class ConsistencyException(msg: String) : KoekoekException(msg)
class DanglingBodyFileReferenceException(msg: String) : KoekoekException(msg)

fun originpath(origin: String): String {
    val uri_origin = URI.create(origin)
    val portpart = uri_origin.port.let {
        when (it) {
            -1 -> ""
            else -> "+${it}"
        }
    }
    return pathjoin("storage", "default", "${uri_origin.scheme}+++${uri_origin.host}${portpart}")
}

fun dbpath_for_origin(origin: String): String {
    return pathjoin(originpath(origin), "cache", CACHE_DB_FILENAME)
}

fun morguepath_for_origin(origin: String): String {
    return pathjoin(originpath(origin), "cache", "morgue")
}

fun MessageDigest.writethrough(inbuf: ByteArray, outstream: OutputStream): ByteArray? {
    this.update(inbuf)
    outstream.write(inbuf)
    return this.digest()
}

fun MessageDigest.writethrough(instream: InputStream, outstream: OutputStream): ByteArray {
    val buf = ByteArray(4096)
    var amt_read: Int
    while (instream.read(buf).also { amt_read = it } != -1) {
        this.update(buf, 0, amt_read)
        outstream.write(buf, 0, amt_read)
    }
    return this.digest().also {
        this.update(it)  // Seed the hash back in; we want the origin state to cascade
    }
}


class SignedDigestChainZipper(val certchain_pem: ByteArray, val outstream: OutputStream, val signer: Signature) {
    val digester: MessageDigest = MessageDigest.getInstance(BUNDLE_DIGEST_ALGO)
    val zipOutputStream = ZipOutputStream(outstream).apply {
        setMethod(ZipOutputStream.DEFLATED)
        setLevel(Deflater.NO_COMPRESSION)
    }
    var digest_so_far: ByteArray? = null
    var entry_count: BigInteger = BigInteger.ZERO
    var current_entry: ZipEntry? = null
    var current_entry_no = 0

    fun setEntryTimestamp(entry: ZipEntry) {
        entry.setLastModifiedTime(FileTime.fromMillis(current_entry_no * 60_000L))
        current_entry_no++
    }

    fun sign() {
        digest_so_far?.apply {
            current_entry?.also {
                zipOutputStream.closeEntry()
                zipOutputStream.putNextEntry(ZipEntry("${it.name}.sig").also {
                    setEntryTimestamp(it)
                })
                zipOutputStream.write(signer.run {
                    update(digest_so_far)
                    return@run sign()
                })
            }
        }
    }

    fun putNextEntry(
            entry: ZipEntry, instream: InputStream, sign: Boolean = true) {
        zipOutputStream.closeEntry()
        zipOutputStream.putNextEntry(entry.also {
            setEntryTimestamp(it)
        })
        current_entry = entry
        digest_so_far = digester.writethrough(instream, zipOutputStream)
        entry_count++
        if (sign) sign()
    }

    fun putNextEntry(entry: ZipEntry, inbuf: ByteArray, sign: Boolean = true) {
        return putNextEntry(entry, inbuf.inputStream(), sign = sign)
    }

    fun close() {
        zipOutputStream.close()
    }

    init {
        this.putNextEntry(ZipEntry(CERTCHAIN_ENTRY_NAME), this.certchain_pem, sign = false)
        this.putNextEntry(ZipEntry("$DUMPMETA_VERSION"), ByteArray(0), sign = false)
    }
}

fun packup(
        cacheDBOps: CacheDBOps,
        profiledir: File,
        outstream: OutputStream,
        pkiOps: PKIOps,
        target: PackupTargetDesignation,
        headerFilter: HeaderFilter? = null): DumpDescriptor {
    val swdesc = target.takeIf { it.type == CacheType.SWORK }
        ?.let { ServiceworkerRegistry(profiledir).get_active_entry(target.origin, target.name) }
    val morgue_basepath = File(profiledir, morguepath_for_origin(target.origin))
    val (cachemeta, security_map, entries) = cacheDBOps.extract_dbinfo(
        profiledir, target.origin, swdesc?.regid() ?: target.name, target.type, headerFilter
    )
    val dumpdescriptor = DumpDescriptor(
        cacheDescriptor = cachemeta.also { it.user_version = target.version },
        serviceworkerDescriptor = swdesc,
        signatureAlgorithm = pkiOps.devkeysignatureAlgorithm,
    )
    val certchain_pem = pkiOps.getPemChainForDevCert().joinToString("\n").toByteArray(PEM_SERIALIZATION_CHARSET)
    val chainZipper = SignedDigestChainZipper(certchain_pem, outstream, pkiOps.getSigner())
    chainZipper.putNextEntry(
        ZipEntry(META_ENTRY_NAME), Json.encodeToString(dumpdescriptor).toByteArray(Charsets.UTF_8)
    )
    chainZipper.putNextEntry(
        ZipEntry(SECURITY_INFO_ENTRY_NAME), Json.encodeToString(security_map).toByteArray(Charsets.UTF_8)
    )
    entries.forEachIndexed { i, it ->
        try {
            it.determine_bodysizes(morgue_basepath)
            chainZipper.putNextEntry(
                ZipEntry("${i}.json"), Json.encodeToString(it).toByteArray(Charsets.UTF_8)
            )
            it.request_morguepath(morgue_basepath)?.also {
                it.inputStream().use {
                    chainZipper.putNextEntry(
                        ZipEntry("${i}.${REQUEST_BODY_ENTRY_NAME}"), BufferedInputStream(it)
                    )
                }
            }
            it.response_morguepath(morgue_basepath)?.also {
                it.inputStream().use {
                    chainZipper.putNextEntry(
                        ZipEntry("${i}.${RESPONSE_BODY_ENTRY_NAME}"), BufferedInputStream(it)
                    )
                }
            }
        } catch (e: FileNotFoundException) {
            throw DanglingBodyFileReferenceException(e.message ?: "")
        }
    }
    chainZipper.close()
    if (swdesc != target.takeIf { it.type == CacheType.SWORK }?.let {
            ServiceworkerRegistry(profiledir).get_active_entry(target.origin, target.name)
        }) throw ConsistencyException("Smear risk: Serviceworker database changed while serializing serviceworker")
    return dumpdescriptor
}