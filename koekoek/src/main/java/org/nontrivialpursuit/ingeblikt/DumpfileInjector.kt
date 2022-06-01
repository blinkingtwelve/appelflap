package org.nontrivialpursuit.ingeblikt

import org.nontrivialpursuit.ingeblikt.PKIOps.PKIOps
import org.nontrivialpursuit.ingeblikt.interfaces.AppelflapBridge
import org.nontrivialpursuit.ingeblikt.interfaces.CacheDBOps
import org.nontrivialpursuit.ingeblikt.interfaces.RebootMethod
import java.io.File
import java.io.InputStream
import java.util.*

class DumpfileInjector(
        instream: InputStream, pkiOps: PKIOps, val profiledir: File, val dbOps: CacheDBOps, val appelflapBridge: AppelflapBridge? = null) {

    val unpacker = DumpfileUnpacker(instream = instream, pkiOps = pkiOps)
    val sworker = unpacker.dumpdescriptor.serviceworkerDescriptor?.let { it.renamed("{${UUID.randomUUID()}}") }
        ?.also { it.isValid() || throw ServiceworkerSerdeException("Serviceworker unpalatable: ${it.dump()}") }

    fun inject(reboot_style: RebootMethod = RebootMethod.NOOP): DumpDescriptor {
        val torun: () -> Unit = {
            sworker?.let {
                inject_cache(rename_to = sworker.regid())
                inject_serviceworker()
            } ?: inject_cache()
        }
        appelflapBridge?.runWithHibernatingGeckoSession(reboot_style = reboot_style, torun) ?: torun()
        return unpacker.dumpdescriptor
    }


    fun inject_cache(rename_to: String? = null) {
        val cachedir = get_or_create_storage_area()

        @Suppress("DEPRECATION") val stagingDir = createTempDir("morgue-staging.", "", profiledir)
        try {
            val cache_entries = unpacker.processCacheEntries(staging_dir = stagingDir)
            dbOps.inject(
                cachedir = cachedir,
                cacheDescriptor = unpacker.dumpdescriptor.cacheDescriptor,
                securityInfoMap = unpacker.security_info_map,
                entries = cache_entries,
                rename_to = rename_to
            )
            stagingDir.listFiles { thefile -> thefile.isDirectory }.forEach { staging_bodydir ->
                val target_dir = File(cachedir.morgue, staging_bodydir.name)
                if (!target_dir.exists()) {
                    staging_bodydir.renameTo(target_dir) || throw RuntimeException("Unexpected failure moving directory")
                } else {
                    staging_bodydir.listFiles { thefile -> thefile.isFile }.forEach { staging_bodyfile ->
                        staging_bodyfile.renameTo(
                            File(
                                target_dir, staging_bodyfile.name
                            )
                        ) || throw RuntimeException("Unexpected failure moving file")
                    }
                }
            }
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    fun get_or_create_storage_area(): Cachedir {
        val cachedir = Cachedir(File(profiledir, pathjoin(originpath(unpacker.dumpdescriptor.cacheDescriptor.origin), CACHE_DIR_NAME)))
        if (cachedir.morgue.mkdirs()) {
            // Layout was not complete, so we'll need to initialize a DB
            dbOps.initCacheDB(cachedir.db)
        }
        return cachedir
    }

    fun inject_serviceworker() {
        sworker?.also { ServiceworkerRegistry(profiledir).stage_entry(sworker) }
    }
}