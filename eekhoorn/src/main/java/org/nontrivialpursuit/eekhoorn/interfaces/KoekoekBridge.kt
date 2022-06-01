package org.nontrivialpursuit.eekhoorn.interfaces

import org.nontrivialpursuit.ingeblikt.*
import org.nontrivialpursuit.ingeblikt.PKIOps.PKIOps
import org.nontrivialpursuit.ingeblikt.interfaces.AppelflapBridge
import org.nontrivialpursuit.ingeblikt.interfaces.CacheDBOps
import org.nontrivialpursuit.ingeblikt.interfaces.RebootMethod
import java.io.File
import java.io.InputStream
import java.util.*


interface KoekoekBridge {
    val profile_dir: File
    val bundle_dir: File
    val pkiOps: PKIOps
    val dbOps: CacheDBOps
    val subscriptionsRegistryFile: File
    val headerFilter: HeaderFilter?

    fun listBundles(): List<BundleIndexItem>

    fun serializeCache(target: PackupTargetDesignation)

    fun serializeCacheRetryOnMissingBodyfile(target: PackupTargetDesignation) {
        var retry = true
        while (retry) {
            try {
                serializeCache(target)
                retry = false
            } catch (e: DanglingBodyFileReferenceException) {
                retry = true
            }
        }
    }

    fun garbageCollect()

    fun saveSubscriptions(
            subs: ByteArray,
            gc_permits: Int = 1,
            mutex_permits: Int = 1,
            previous_version_hash: String? = null,
            run_callbacks: Boolean = true): Boolean

    fun saveSubscriptions(
            subs: String, gc_permits: Int = 1, mutex_permits: Int = 1, previous_version_hash: String? = null, run_callbacks: Boolean = true): Boolean

    fun saveSubscriptions(
            subs: Subscriptions,
            gc_permits: Int = 1,
            mutex_permits: Int = 1,
            previous_version_hash: String? = null,
            run_callbacks: Boolean = true): Boolean

    fun getSubscriptions(mutex_permits: Int = 1): Subscriptions

    fun addBundle(
            instream: InputStream, archive_size: Long? = null, bundle: BundleDescriptor? = null, run_callbacks: Boolean = true): BundleDescriptor

    fun deleteBundle(bundle: BundleDescriptor): Boolean
    fun getDumpFile(bundle: BundleDescriptor): File?

    fun injectDump(instream: InputStream): DumpDescriptor
    fun injectDump(instream: InputStream, appelflapBridge: AppelflapBridge, reboot_style: RebootMethod): DumpDescriptor

    fun quiet_time(): Long {
        return (Date().time - bundle_dir.lastModified()) / 1000
    }
}