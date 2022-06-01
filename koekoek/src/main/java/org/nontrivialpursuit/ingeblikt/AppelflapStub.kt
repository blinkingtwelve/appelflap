package org.nontrivialpursuit.ingeblikt

import org.nontrivialpursuit.ingeblikt.interfaces.AppelflapBridge
import org.nontrivialpursuit.ingeblikt.interfaces.RebootMethod
import java.io.InputStream
import java.util.concurrent.Semaphore
import kotlin.collections.HashMap

class AppelflapStub() : AppelflapBridge {

    override val packageApkInfo = null

    override val injectionLock = Semaphore(1)

    override fun runWithHibernatingGeckoSession(reboot_style: RebootMethod, runnable: Runnable) {
        runnable.run()
    }

    override fun injectAll(
            honor_injectionlock: Boolean, reboot_style: RebootMethod, include_serviceworkers: Boolean): HashMap<BundleDescriptor, Boolean> {
        return HashMap()
    }

    override fun inject(input: InputStream, honor_injectionlock: Boolean, reboot_style: RebootMethod) {
    }


}