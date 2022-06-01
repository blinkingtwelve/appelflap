package org.nontrivialpursuit.eekhoorn

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlinx.serialization.SerializationException
import org.nontrivialpursuit.eekhoorn.interfaces.KoekoekBridge
import org.nontrivialpursuit.ingeblikt.*
import org.nontrivialpursuit.ingeblikt.DumpfileUnpacker.Companion.verifyDump
import org.nontrivialpursuit.ingeblikt.PKIOps.PKIOps
import org.nontrivialpursuit.ingeblikt.interfaces.AppelflapBridge
import org.nontrivialpursuit.ingeblikt.interfaces.CacheDBOps
import org.nontrivialpursuit.ingeblikt.interfaces.RebootMethod
import org.nontrivialpursuit.libkitchensink.hexlify
import java.io.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.collections.HashMap

class ConcurrentEditException(msg: String) : KoekoekException(msg)

val EMPTY_SUBSCRIPTION_BYTES = Json.encodeToString(
    Subscriptions.serializer(), Subscriptions(HashMap())
).toByteArray(JSON_SERIALIZATION_CHARSET)
val EMPTY_SUBSCRIPTION_DIGEST = EMPTY_SUBSCRIPTION_BYTES.md5().hexlify()


class KoekoeksNest(
        override val dbOps: CacheDBOps,
        override val profile_dir: File,
        override val bundle_dir: File,
        override val pkiOps: PKIOps,
        override val headerFilter: HeaderFilter? = HeaderFilter.sane_default()) : KoekoekBridge {

    val GCLock = Lockchest.get(this::class.qualifiedName + "_GC")
    val subscriptions_mutex = Lockchest.get(this::class.qualifiedName + "_SUBSCRIPTIONS")
    override val subscriptionsRegistryFile = File(bundle_dir, SUBSCRIPTIONS_REGISTRY)
    val CollectionUpdateCallbacks = ConcurrentHashMap<String, () -> Unit>()
    val SubscriptionUpdateCallbacks = ConcurrentHashMap<String, () -> Unit>()
    val executor = Executors.newFixedThreadPool(1)

    fun do_subscriptionupdate_callbacks() {
        SubscriptionUpdateCallbacks.values.forEach {
            executor.execute { runCatching { it.invoke() } }
        }
    }

    fun do_collectionupdate_callbacks() {
        CollectionUpdateCallbacks.values.forEach {
            executor.execute { runCatching { it.invoke() } }
        }
    }

    fun PackupTargetDesignation.createIdentityTempdir(): File {
        return createTempdir(bundle_dir, "${this.fs_identity()}${TEMP_SUFFIX}")
    }

    fun BundleDescriptor.getIdentityDir(): File {
        return File(bundle_dir, this.fs_identity())
    }

    fun BundleDescriptor.getExpelDir(): File {
        return File(bundle_dir, "${this.fs_identity()}${EXPEL_SUFFIX}")
    }

    fun PackupTargetDesignation.getExpelDir(): File {
        return File(bundle_dir, "${this.fs_identity()}${EXPEL_SUFFIX}")
    }

    override fun injectDump(instream: InputStream): DumpDescriptor {
        return DumpfileInjector(instream, pkiOps, profile_dir, dbOps).inject()
    }

    override fun injectDump(
            instream: InputStream, appelflapBridge: AppelflapBridge, reboot_style: RebootMethod): DumpDescriptor {
        return DumpfileInjector(instream, pkiOps, profile_dir, dbOps, appelflapBridge).inject(reboot_style = reboot_style)
    }

    override fun listBundles(): List<BundleIndexItem> {
        return bundle_dir.listFiles { file ->
            file.isDirectory && file.name.matches(KOEKOEK_BUNDLE_REGEX)
        }.map {
            File(it, BUNDLE_META_FILENAME) to File(it, BUNDLE_DUMP_FILENAME)
        }.filter {
            it.first.exists() && it.second.exists()
        }.sortedBy {
            it.first.parentFile.name
        }.map {
            try {
                val bundledesc = Json.decodeFromString(DumpDescriptor.serializer(), it.first.readText())
                return@map BundleIndexItem(
                    BundleDescriptor(
                        bundledesc.cacheDescriptor.namespace,
                        bundledesc.cacheDescriptor.origin,
                        bundledesc.name,
                        bundledesc.cacheDescriptor.version
                    ), it.second.length(), it.second.lastModified()
                )
            } catch (e: SerializationException) {
                // Unsavoury (corrupted, or non-compatible) bundle. Schedule for deletion.
                it.first.parentFile.renameTo(File(it.first.parent + EXPEL_SUFFIX))
                return@map null
            }
        }.filterNotNull()
    }

    fun serializeIdentityOperation(identity: String, runthis: () -> Unit) {
        try {
            Lockchest.acquire(identity)
            return runthis.invoke()
        } finally {
            Lockchest.release(identity)
        }
    }

    @Synchronized
    override fun serializeCache(target: PackupTargetDesignation) {
        serializeIdentityOperation(target.fs_identity()) {
            var tempdir: File? = null
            GCLock.runWith({
                            try {
                                tempdir = target.createIdentityTempdir().also {
                                    val outstream = BufferedOutputStream(File(it, BUNDLE_DUMP_FILENAME).outputStream())
                                    val descriptor = packup(dbOps, profile_dir, outstream, pkiOps, target, headerFilter)
                                    File(it, BUNDLE_META_FILENAME).writeText(encodeToString(DumpDescriptor.serializer(), descriptor))
                                    val bundle = descriptor.toBundleDescriptor()
                                    val target_dir = bundle.getIdentityDir()
                                    if (!it.renameTo(target_dir)) {
                                        val expeldir = target.getExpelDir()
                                        expeldir.deleteRecursively()
                                        target_dir.renameTo(expeldir)
                                        it.renameTo(target_dir)
                                    }
                                }
                                do_collectionupdate_callbacks()
                            } finally {
                                tempdir?.deleteRecursively()
                            }
                        })
        }
    }

    override fun deleteBundle(bundle: BundleDescriptor): Boolean {
        if (!bundle.getIdentityDir().exists()) return false
        serializeIdentityOperation(bundle.fs_identity(), {
            val expeldir = bundle.getExpelDir()
            expeldir.deleteRecursively()
            bundle.getIdentityDir().renameTo(expeldir)
            expeldir.deleteRecursively()
        })
        do_collectionupdate_callbacks()
        return true
    }

    override fun getDumpFile(bundle: BundleDescriptor): File? {
        val ei_files = bundle.getIdentityDir().let { basedir: File ->
            File(basedir, BUNDLE_META_FILENAME) to File(
                basedir, BUNDLE_DUMP_FILENAME
            )
        }
        if (!ei_files.toList().all { it.exists() }) return null
        val (descriptorfile, dumpfile) = ei_files
        val descriptor = Json.decodeFromString(DumpDescriptor.serializer(), descriptorfile.readText())
        if (descriptor.cacheDescriptor.version != bundle.version) return null
        return dumpfile
    }

    override fun addBundle(
            instream: InputStream, archive_size: Long?, bundle: BundleDescriptor?, run_callbacks: Boolean): BundleDescriptor {
        @Suppress("DEPRECATION") val tempdir = createTempDir(suffix = ".incoming_bundle", directory = bundle_dir)
        try {
            GCLock.acquire()
            val dumpfile = File(tempdir, BUNDLE_DUMP_FILENAME)
            if (dumpfile.outputStream().buffered().use {
                    it.slurp(instream.buffered(), archive_size)
                } != MEAL_SIZE.RIGHT_ON) throw IOException("Mismatched read size")
            val dumpDescriptor = verifyDump(
                instream = dumpfile.inputStream().buffered(), pkiOps = pkiOps
            ).first.first
            val bundle_per_input = dumpDescriptor.let {
                BundleDescriptor(it.cacheDescriptor.namespace, it.cacheDescriptor.origin, it.name, it.cacheDescriptor.version)
            }
            bundle?.also {
                if (it != bundle_per_input) throw RuntimeException("BundleDescriptor mismatches archive descriptor")
            }
            File(tempdir, BUNDLE_META_FILENAME).bufferedWriter(JSON_SERIALIZATION_CHARSET).use {
                it.write(Json.encodeToString(DumpDescriptor.serializer(), dumpDescriptor))
            }
            serializeIdentityOperation(bundle_per_input.fs_identity(), {
                val target_dir = bundle_per_input.getIdentityDir()
                if (!tempdir.renameTo(target_dir)) {
                    val expeldir = bundle_per_input.getExpelDir()
                    expeldir.deleteRecursively()
                    target_dir.renameTo(expeldir)
                    tempdir.renameTo(target_dir)
                    expeldir.deleteRecursively()
                }
            })
            saveSubscriptionRecord(bundle_per_input, flags = RECORD_PUBLISHED, gc_permits = 0)
            if (run_callbacks) do_collectionupdate_callbacks()
            return bundle_per_input
        } finally {
            GCLock.release()
            tempdir.deleteRecursively()
        }
    }

    override fun getSubscriptions(mutex_permits: Int): Subscriptions {
        try {
            subscriptions_mutex.acquire(mutex_permits)
            return kotlin.runCatching {
                Json.decodeFromString(
                    Subscriptions.serializer(), subscriptionsRegistryFile.reader(JSON_SERIALIZATION_CHARSET).readText()
                )
            }.getOrNull() ?: Subscriptions(HashMap())
        } finally {
            subscriptions_mutex.release(mutex_permits)
        }
    }

    override fun saveSubscriptions(
            subs: String, gc_permits: Int, mutex_permits: Int, previous_version_hash: String?, run_callbacks: Boolean): Boolean {
        return saveSubscriptions(subs.toByteArray(JSON_SERIALIZATION_CHARSET), gc_permits, mutex_permits, run_callbacks = run_callbacks)
    }

    override fun saveSubscriptions(
            subs: Subscriptions, gc_permits: Int, mutex_permits: Int, previous_version_hash: String?, run_callbacks: Boolean): Boolean {
        return saveSubscriptions(
            Json.encodeToString(Subscriptions.serializer(), subs), gc_permits, mutex_permits, run_callbacks = run_callbacks
        )
    }

    override fun saveSubscriptions(
            subs: ByteArray, gc_permits: Int, mutex_permits: Int, previous_version_hash: String?, run_callbacks: Boolean): Boolean {
        subscriptions_mutex.acquire(mutex_permits)
        GCLock.acquire(gc_permits)
        try {
            previous_version_hash?.also {
                val current_hash = try {
                    subscriptionsRegistryFile.readBytes().md5().hexlify()
                } catch (e: FileNotFoundException) {
                    EMPTY_SUBSCRIPTION_DIGEST
                }
                if (current_hash != previous_version_hash) throw ConcurrentEditException("Subscriptions have been updated concurrently")
            }
            @Suppress("DEPRECATION") val tempfile = createTempFile(
                "${subscriptionsRegistryFile.name}_temp", TEMP_SUFFIX, bundle_dir
            )
            tempfile.writeBytes(subs)
            return (tempfile.renameTo(subscriptionsRegistryFile)).also {
                if (it and run_callbacks) do_subscriptionupdate_callbacks()
            }
        } finally {
            subscriptions_mutex.release(mutex_permits)
            GCLock.release(gc_permits)
        }
    }

    fun saveSubscriptionRecord(bundle: BundleDescriptor, flags: Int, gc_permits: Int = 1, mutex_permits: Int = 0) {
        // Use the subscriptions mutex to prevent the subscriptions from being modified behind our back while we are modifying them ourselves
        subscriptions_mutex.runWith({
                                        saveSubscriptions(
                                            getSubscriptions(mutex_permits = 0).bump_version(bundle.type, bundle.origin, bundle.name, bundle.version, flags),
                                            mutex_permits = 0,
                                            gc_permits = gc_permits
                                        )
                                    }, nb_permits = mutex_permits)
    }

    override fun garbageCollect(): Unit {
        if (GCLock.tryAcquire()) {
            try {
                bundle_dir.listFiles { thefile ->
                    (BUNDLE_GC_REGEX.matchEntire(thefile.name) != null)
                }.forEach {
                    it.deleteRecursively()
                }
            } finally {
                GCLock.release()
            }
        }
        deletables(getSubscriptions(mutex_permits = 0), listBundles()).map {
            deleteBundle(it)
        }.find { it }?.also {
            do_collectionupdate_callbacks()
        }
    }

    fun clearSubscriptions() {
        subscriptionsRegistryFile.delete()
    }

    fun clearBundles() {
        bundle_dir.deleteRecursively()
        bundle_dir.mkdir()
    }

    init {
        bundle_dir.mkdir()
    }
}