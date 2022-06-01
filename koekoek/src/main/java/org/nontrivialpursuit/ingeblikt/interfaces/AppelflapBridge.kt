package org.nontrivialpursuit.ingeblikt.interfaces

import org.nontrivialpursuit.ingeblikt.BundleDescriptor
import org.nontrivialpursuit.libkitchensink.BonjourSighting
import org.nontrivialpursuit.libkitchensink.DownloadDisplayDescriptorListing
import org.nontrivialpursuit.libkitchensink.WifiDigest
import java.io.File
import java.io.InputStream
import java.util.*
import java.util.concurrent.Semaphore
import kotlin.collections.HashMap

enum class RebootMethod {
    NOOP,
    ENGINE_REBOOT,
    APP_REBOOT,
}

enum class PackupContentionStrategy {
    NOOP,
    ENGINE_REBOOT,
    ENGINE_REBOOT_UPON_CONTENTION,
}

interface AppelflapBridge {

    val packageApkInfo: Triple<File, String, String>?

    val injectionLock: Semaphore  // implementor should make this a singleton

    fun setInjectionLock() {
        injectionLock.tryAcquire()  // doesn't matter whether we already have a permit
    }

    fun releaseInjectionLock() {
        injectionLock.release()
    }

    fun runWithHibernatingGeckoSession(reboot_style: RebootMethod = RebootMethod.NOOP, runnable: Runnable)

    fun injectAll(honor_injectionlock: Boolean = true, reboot_style: RebootMethod = RebootMethod.NOOP, include_serviceworkers: Boolean = false): HashMap<BundleDescriptor, Boolean>

    fun inject(input: InputStream, honor_injectionlock: Boolean = true, reboot_style: RebootMethod = RebootMethod.NOOP)


    fun launchWifiPicker() {
    }

    fun launchStorageManager(): Boolean {
        return false
    }

    fun getWifiDigest(): WifiDigest? {
        return null
    }

    fun getBonjourPeers(): List<BonjourSighting> {
        return LinkedList()
    }

    fun setLanguage(lang: String): Boolean {
        return false
    }

    fun getDownloadListing(): DownloadDisplayDescriptorListing {
        return DownloadDisplayDescriptorListing(LinkedList())
    }

    fun deleteDownload(actionSubject: Pair<String, String>): Boolean {
        return false
    }

    fun openDownload(actionSubject: Pair<String, String>): Boolean {
        return false
    }

    fun shareDownload(actionSubject: Pair<String, String>): Boolean {
        return false
    }
}