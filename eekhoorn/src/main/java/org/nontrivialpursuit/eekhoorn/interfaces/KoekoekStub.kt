package org.nontrivialpursuit.eekhoorn.interfaces

import org.nontrivialpursuit.ingeblikt.*
import org.nontrivialpursuit.ingeblikt.PKIOps.PKIOps
import org.nontrivialpursuit.ingeblikt.interfaces.AppelflapBridge
import org.nontrivialpursuit.ingeblikt.interfaces.CacheDBOps
import org.nontrivialpursuit.ingeblikt.interfaces.RebootMethod
import java.io.File
import java.io.InputStream
import java.util.*

@Suppress("StopShip")
class KoekoekStub(override val headerFilter: HeaderFilter? = null) : KoekoekBridge {
    override val profile_dir = File("/nope")
    override val bundle_dir = File("/nope")
    override val pkiOps: PKIOps
        get() = TODO("Not implemented")
    override val dbOps: CacheDBOps
        get() = TODO("Not yet implemented")
    override val subscriptionsRegistryFile: File
        get() = TODO("Not yet implemented")

    override fun listBundles(): List<BundleIndexItem> {
        return LinkedList()
    }

    override fun serializeCache(target: PackupTargetDesignation) {
    }

    override fun garbageCollect() {}
    override fun saveSubscriptions(
            subs: ByteArray, gc_permits: Int, mutex_permits: Int, previous_version_hash: String?, run_callbacks: Boolean): Boolean {
        return false
    }

    override fun saveSubscriptions(
            subs: String, gc_permits: Int, mutex_permits: Int, previous_version_hash: String?, run_callbacks: Boolean): Boolean {
        return false
    }

    override fun saveSubscriptions(
            subs: Subscriptions, gc_permits: Int, mutex_permits: Int, previous_version_hash: String?, run_callbacks: Boolean): Boolean {
        return false
    }

    override fun getSubscriptions(mutex_permits: Int): Subscriptions {
        TODO("Not yet implemented")
    }

    override fun addBundle(
            instream: InputStream, archive_size: Long?, bundle: BundleDescriptor?, run_callbacks: Boolean): BundleDescriptor {
        TODO("Not yet implemented")
    }

    override fun deleteBundle(bundle: BundleDescriptor): Boolean {
        return false
    }

    override fun getDumpFile(bundle: BundleDescriptor): File {
        TODO("Not implemented")
    }

    override fun injectDump(instream: InputStream): DumpDescriptor {
        TODO("Not yet implemented")
    }

    override fun injectDump(
            instream: InputStream, appelflapBridge: AppelflapBridge, reboot_style: RebootMethod): DumpDescriptor {
        TODO("Not yet implemented")
    }

}