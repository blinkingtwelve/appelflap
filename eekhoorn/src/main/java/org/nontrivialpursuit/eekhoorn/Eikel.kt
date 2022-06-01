package org.nontrivialpursuit.eekhoorn

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.eclipse.jetty.io.RuntimeIOException
import org.eclipse.jetty.util.log.Log
import org.nontrivialpursuit.ingeblikt.*
import org.nontrivialpursuit.libkitchensink.hexlify
import java.io.*
import java.util.*

@Serializable
data class EikelMeta(val path: String, val headers: Map<String, String>)

enum class EikelMode {
    R,
    W
}

class Eikel(val basedir: File, urlpath: String, val mode: EikelMode) {
    val logger = Log.getLogger(this.javaClass)
    val eikel_digest = urlpath.toByteArray(Charsets.UTF_8).md5().hexlify()
    val gc_lock = Lockchest.get(this::class.qualifiedName + "_eikel_GC")
    var holds_lock = false
    val eikel_dir: File = when (mode) {
        EikelMode.R -> File(basedir, eikel_digest)
        EikelMode.W -> {
            gc_lock.acquire() // take out shared lock to prevent GC from deleting the temp dir
            holds_lock = true
            createTempdir(basedir, eikel_digest + TEMP_SUFFIX)
        }
    }
    val body_file = File(eikel_dir, EIKEL_BODY_FILENAME)
    val meta_file = File(eikel_dir, EIKEL_META_FILENAME)

    fun body_size(): Long {
        return body_file.length()
    }

    val headers: Map<String, String>
        get() {
            try {
                return Json.decodeFromString(EikelMeta.serializer(), meta_file.readText()).headers
            } catch (ignored: FileNotFoundException) {
                return HashMap<String, String>() // then there'll be no headers.
            }
        }

    fun storeMeta(themeta: EikelMeta) {
        meta_file.writeText(Json.encodeToString(themeta))
    }

    fun finalize_upload() {
        // Move old directory (if any), move this new upload in its place, then delete old directory
        // File handles on the to-be-retired version of this resource (if any) will stay valid even though
        // the underlying directory are being moved and deleted.
        // This results in desirable properties:
        // 1. we can have multiple concurrent uninterrupted PUTs and GETs for the same resource
        // 2. odering is guaranteed in the sense that you'll only see the result of a PUT if you GET it after
        // said PUT completes, and you can't get incomplete results (say from an ongoing PUT, or crashed PUT).
        try {
            val destdir = File(basedir, eikel_digest)
            val expeldir = createTempdir(basedir, "${eikel_digest}${EXPEL_SUFFIX}")
            if (destdir.exists()) destdir.renameTo(File(expeldir, "expel"))
            // short window of time during which the file does not exist for any fresh GET
            eikel_dir.renameTo(destdir)
            expeldir.deleteRecursively()
        } finally {
            gc_lock.release()
            holds_lock = false
        }
    }

    fun delete() {
        eikel_dir.deleteRecursively()
    }

    protected fun finalize() {
        if (holds_lock) {
            gc_lock.release()
        }
    }

    init {
        if (mode == EikelMode.R && !meta_file.canRead()) throw NoSuchEikelException(eikel_dir.toString())
    }

    companion object {
        fun garbagecollect(basedir: File) {
            Lockchest.get("EIKEL_GC").runWith({
                            basedir.listFiles { file, _ ->
                                file.isDirectory && file.name.matches(BUNDLE_GC_REGEX)
                            }?.forEach { it.deleteRecursively() }
                        })
        }

        fun get_eikels(basedir: File): List<Pair<Eikel, EikelMeta>> {
            return basedir.listFiles { file -> file.isDirectory && file.name.matches(MD5_REGEX) }?.toList()?.mapNotNull {
                kotlin.runCatching {
                    val meta = Json.decodeFromString(EikelMeta.serializer(), File(it, EIKEL_META_FILENAME).readText())
                    Eikel(
                        basedir, meta.path, EikelMode.R
                    ) to meta
                }.getOrNull()
            } ?: LinkedList()
        }
    }
}

class NoSuchEikelException(msg: String) : RuntimeIOException(msg)