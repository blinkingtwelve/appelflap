package org.nontrivialpursuit.appelflap

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.AssetManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Process.killProcess
import android.os.storage.StorageManager
import com.yariksoffice.lingver.Lingver
import org.mozilla.geckoview.*
import org.nontrivialpursuit.appelflap.peddlenet.ConductorService
import org.nontrivialpursuit.appelflap.webwrap.*
import org.nontrivialpursuit.eekhoorn.HttpEekhoorn
import org.nontrivialpursuit.eekhoorn.KoekoeksNest
import org.nontrivialpursuit.eekhoorn.interfaces.HttpEekhoornInterface
import org.nontrivialpursuit.ingeblikt.*
import org.nontrivialpursuit.ingeblikt.interfaces.AppelflapBridge
import org.nontrivialpursuit.ingeblikt.interfaces.RebootMethod
import org.nontrivialpursuit.libkitchensink.BonjourSighting
import org.nontrivialpursuit.libkitchensink.DownloadDisplayDescriptorListing
import org.nontrivialpursuit.libkitchensink.WifiDigest
import org.nontrivialpursuit.libkitchensink.streamStreamToStream
import java.io.*
import java.util.*
import kotlin.collections.HashMap
import kotlin.system.exitProcess

val TABPROCESS_REGEX = Regex("^${Regex.escape(BuildConfig.APPLICATION_ID)}:tab(\\d+)$")
const val MOZ_FILES = "mozilla"
const val PROFILES_INI = "profiles.ini"
const val USER_JS = "user.js"
const val DEFAULT_PROFILE_DIR = "default_profile"
const val PRELOAD_ASSET_PATH = "ingeblikt_preload"

class GeckoExceptionListener : GeckoResult.OnExceptionListener<Unit> {
    val log = Logger(this)
    override fun onException(exception: Throwable): GeckoResult<Unit>? {
        log.e(exception.toString())
        throw(exception)
    }
}

class GeckoRuntimeManager private constructor(
        val application: Appelflap, val extras: Bundle?) : GeckoRuntime.Delegate, AppelflapBridge {
    val mozdir = File(application.filesDir, MOZ_FILES)
    val log = Logger(
        this.javaClass
    )
    var wrapper: GeckoWrap? = null
    val eekhoorn: HttpEekhoornInterface
    val koekoeksNest: KoekoeksNest
        get() {
            return Appelflap.get(application).koekoeksNest
        }
    val swUpdateNotification = SWUpdateNotification(application, koekoeksNest)
    lateinit var profiledir: File
    private val HIBERNATE_MUTEX = Lockchest.get(this::class.qualifiedName + "_HIBERNATE_MUTEX")

    var geckoRuntime: GeckoRuntime
        private set
    override val packageApkInfo = Triple<File, String, String>(
        File(application.applicationInfo.sourceDir), BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME
    )
    override val injectionLock = Lockchest.get(this::class.qualifiedName + "_WEBAPP_INJECTION_LOCK")

    private fun killTabProcesses() {
        // Serves to relinquish any in-process reflections of on-disk state which we might be poking.
        // It would seem that GeckoRuntime.shutdown() -> GeckoRuntime.create() would be less rude,
        // but create() is a troublesome in that once bound to the Application it doesn't allow any
        // new instance even after a shutdown().
        while (true) {
            val pidlist = pgrep(TABPROCESS_REGEX)
            if (pidlist.size == 0) break
            pidlist.forEach { pid ->
                killProcess(pid)
            }
        }
    }

    @SuppressLint("StopShip")
    @Synchronized
    override fun runWithHibernatingGeckoSession(reboot_style: RebootMethod, runnable: Runnable) {
        val outcome = when (reboot_style) {
            RebootMethod.NOOP -> kotlin.runCatching { runnable.run() }
            else -> {
                try {
                    HIBERNATE_MUTEX.acquire()
                    val seshstate = wrapper?.stopSession()?.also {
                        log.v(it.toString())
                    }
                    when (reboot_style) {
                        RebootMethod.APP_REBOOT -> {
                            wrapper?.informReboot()
                            killTabProcesses()
                            val runnable_result = kotlin.runCatching { runnable.run() }
                            application.relaunchViaTrampoline()
                            runnable_result
                        }
                        RebootMethod.ENGINE_REBOOT -> {
                            wrapper?.informReboot()
                            killTabProcesses()
                            val runnable_result = kotlin.runCatching { runnable.run() }
                            wrapper?.instantiateSession(this, seshstate)
                            runnable_result
                        }
                        else -> TODO("Hibernate behaviour ${reboot_style} unimplemented")
                    }
                } finally {
                    HIBERNATE_MUTEX.release()
                }
            }
        }
        outcome.getOrThrow()
    }

    fun writeEekhoornInfo(eekhoorn: HttpEekhoornInterface) {
        File(
            application.applicationContext.filesDir, pathjoin(APPELFLAP_WEBEXTENSION_PATH, APPELFLAP_WEBEXTENSION_SERVERINFO_FILENAME)
        ).writeText(
            """
        {
          "username": "${eekhoorn.credentials.first}",
          "password": "${eekhoorn.credentials.second}",
          "port": ${eekhoorn.get_portno()}
        }    
        """.trimIndent()
        )
    }

    fun writePeerIDInfo() {
        File(
            application.applicationContext.filesDir, pathjoin(APPELFLAP_WEBEXTENSION_PATH, APPELFLAP_WEBEXTENSION_PEERID_FILENAME)
        ).writeText(
            """
        {
          "id": ${get_nodeID(application)},
          "friendly_id": "${get_friendly_nodeID(application)}",
          "palette": "${base55_palette.joinToString("")}"
        }    
        """.trimIndent()
        )
    }

    @Synchronized
    private fun createRuntime(): GeckoRuntime {
        val runtimesettings = GeckoRuntimeSettings.Builder().preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_LIGHT)
            .webManifest(false).remoteDebuggingEnabled(BuildConfig.DEBUG).consoleOutput(BuildConfig.DEBUG)
            .aboutConfigEnabled(BuildConfig.DEBUG).debugLogging(BuildConfig.DEBUG).loginAutofillEnabled(true).contentBlocking(
                ContentBlocking.Settings.Builder().antiTracking(ContentBlocking.AntiTracking.NONE)
                    .safeBrowsing(ContentBlocking.SafeBrowsing.NONE).cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_ALL)
                    .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.NONE).build()
            ).build()
        return GeckoRuntime.create(application, runtimesettings).also {
            it.webPushController.setDelegate(FauxWebpushRegistrationHandler(application))
            it.webNotificationDelegate = WebnotificationsHandler(application, it)
            it.webExtensionController.ensureBuiltIn(
                "file://${application.applicationContext.filesDir}/${APPELFLAP_WEBEXTENSION_PATH}/", "appelflap@appelflap.appelflap"
            ).exceptionally(GeckoExceptionListener())
            it.delegate = this
        }
    }

    fun shutdown() {
        log.v("Shutting down GeckoRuntimeManager / 1")
        Appelflap.get(this.application).geckoRuntimeManager = null
        exitProcess(0)
    }

    override fun onShutdown() {
        log.v("Shutting down GeckoRuntimeManager / 2")
    }

    @Synchronized
    override fun injectAll(
            honor_injectionlock: Boolean, reboot_style: RebootMethod, include_serviceworkers: Boolean): HashMap<BundleDescriptor, Boolean> {
        // Inject all there is to inject using specified runtime suspension/hibernation strategy and cache type
        val requested_bundletypes = setOf(CacheType.CACHE) + when (include_serviceworkers) {
            true -> setOf(CacheType.SWORK)
            false -> setOf()
        }
        try {
            koekoeksNest.subscriptions_mutex.acquire()
            val insertion_scripts = injectables(koekoeksNest.getSubscriptions(mutex_permits = 0), koekoeksNest.listBundles()).filter {
                it.type in requested_bundletypes
            }.associateWith {
                koekoeksNest.getDumpFile(it)
            }.filterValues {
                (it != null)
            }.mapValues {
                Runnable {
                    koekoeksNest.injectDump(it.value!!.inputStream().buffered())
                    koekoeksNest.saveSubscriptionRecord(it.key, flags = RECORD_INJECTED, mutex_permits = 0)
                }
            }
            val results_map = HashMap<BundleDescriptor, Boolean>()
            if (insertion_scripts.isNotEmpty()) {
                var shouldUnlock = false
                try {
                    if (!BuildConfig.FRONTEND_USES_INJECTIONLOCK || !honor_injectionlock || injectionLock.tryAcquire()
                            .also { shouldUnlock = it }) {
                        val injection_script = Runnable {
                            insertion_scripts.forEach {
                                results_map[it.key] = runCatching { it.value.run() }.exceptionOrNull()?.let {
                                    log.e("Error during injection", it)
                                    false
                                } ?: true
                            }
                        }
                        runWithHibernatingGeckoSession(reboot_style = reboot_style, injection_script)
                    }
                } finally {
                    if (shouldUnlock) injectionLock.release()
                }
            }
            if (results_map.filterValues { it }.isNotEmpty()) koekoeksNest.garbageCollect()
            return results_map
        } finally {
            koekoeksNest.subscriptions_mutex.release()
        }
    }


    override fun inject(input: InputStream, honor_injectionlock: Boolean, reboot_style: RebootMethod) {
        var shouldUnlock = false
        try {
            if (!BuildConfig.FRONTEND_USES_INJECTIONLOCK || !honor_injectionlock || injectionLock.tryAcquire().also { shouldUnlock = it }) {
                koekoeksNest.injectDump(input.buffered(), this, reboot_style = reboot_style)
            }
        } finally {
            if (shouldUnlock) injectionLock.release()
        }
    }


    override fun launchWifiPicker() {
        application.startActivity(Intent(WifiManager.ACTION_PICK_WIFI_NETWORK).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun launchStorageManager(): Boolean {
        if (Build.VERSION.SDK_INT >= 25) {
            application.startActivity(Intent(StorageManager.ACTION_MANAGE_STORAGE).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        }
        return false
    }

    override fun getWifiDigest(): WifiDigest? {
        return getWifiDigest(application)
    }

    override fun getBonjourPeers(): List<BonjourSighting> {
        return Appelflap.get(application).conductor.bonjour_scanresult.map {
            it.key.substringAfterLast('/').let { nodeid ->
                kotlin.runCatching {
                    BonjourSighting(debase55(nodeid), nodeid)
                }.getOrNull()
            }
        }.filterNotNull()
    }

    override fun setLanguage(lang: String): Boolean {
        if (lang !in BuildConfig.ENABLED_LANGUAGES) return false
        Lingver.getInstance().setLocale(application, lang)
        return true
    }

    override fun getDownloadListing(): DownloadDisplayDescriptorListing {
        return (wrapper?.downloads ?: Downloads(application)).listDownloads()
    }

    override fun deleteDownload(actionSubject: Pair<String, String>): Boolean {
        return (wrapper?.downloads ?: Downloads(application)).deleteDownload(actionSubject)
    }

    override fun openDownload(actionSubject: Pair<String, String>): Boolean {
        try {
            DownloadEntry(wrapper ?: application, actionSubject).androidOpen()
            return true
        } catch (e: DownloadIllegalStateException) {
            return false
        }
    }

    override fun shareDownload(actionSubject: Pair<String, String>): Boolean {
        try {
            DownloadEntry(wrapper ?: application, actionSubject).androidShare()
            return true
        } catch (e: DownloadIllegalStateException) {
            return false
        }
    }

    companion object {
        @Synchronized
        fun getInstance(application: Appelflap, extras: Bundle? = null): GeckoRuntimeManager {
            // Singleton
            return Appelflap.get(application).let { app ->
                app.geckoRuntimeManager ?: GeckoRuntimeManager(application, extras).also {
                    app.geckoRuntimeManager = it
                }
            }
        }
    }


    private fun getProfileDir(): File? {
        // returns profile dir as per profiles.ini, or null if no valid one
        val mozdir = File(application.filesDir, MOZ_FILES)
        return File(mozdir, PROFILES_INI).takeIf { it.isFile and it.canRead() }?.let { profiles_ini ->
            Properties().let { props ->
                profiles_ini.inputStream().use {
                    props.load(it)
                }
                File(mozdir, props.getProperty("Path")).takeIf { it.exists() && it.isDirectory }
            }
        }
    }


    private fun init_gecko_profiledir() {
        // Determines / creates the profile dir
        // Assumes that we either have
        // a) a mozilla/profiles.ini, and exactly 1 existing profile, and a directory named by some Path= key therein, which holds that profile
        // b) the empty state, in which case we'll create a profile dir and a profiles.ini.
        // Safe assumptions as long as we're not creating multiple profiles with this app (and why would we).
        val assman = application.assets
        profiledir = getProfileDir() ?: File(mozdir, DEFAULT_PROFILE_DIR).apply { mkdirs() }.also {
            streamStreamToStream(assman.open(pathjoin(MOZ_FILES, PROFILES_INI)), File(mozdir, PROFILES_INI).outputStream())
        }
        val user_js_out = FileOutputStream(File(profiledir, USER_JS))
        // We set the user agent this way, instead of via GeckoSessionSettingsBuilder.userAgentOverride()
        // Doing it through the preferences avoids a bug that triggers a CORS preflight for the UA header: https://bugzilla.mozilla.org/show_bug.cgi?id=1629921
        user_js_out.writer().apply {
            write("""user_pref("general.useragent.override", "${USERAGENT}");""" + "\n")
            flush()
        }
        streamStreamToStream(assman.open(pathjoin(MOZ_FILES, USER_JS)), user_js_out)
        assman.deepcopy("appelflap_webextension", File(application.filesDir, APPELFLAP_WEBEXTENSION_PATH).apply { mkdirs() })
    }


    private fun copyPreloads() {
        // Place baked-in bundles and subscriptions when there is no subscriptions file at all (a special case of "no subscriptions").
        if (!koekoeksNest.subscriptionsRegistryFile.let { it.exists() and it.isFile }) {
            runCatching { application.assets.open(pathjoin(PRELOAD_ASSET_PATH, SUBSCRIPTIONS_REGISTRY)) }.getOrNull()?.use { instream ->
                koekoeksNest.saveSubscriptions(instream.readBytes(), run_callbacks = false)
            }
            application.assets.list(PRELOAD_ASSET_PATH)?.filter { it.endsWith(".$BUNDLE_DUMP_FILENAME_EXTENSION") }?.forEach {
                koekoeksNest.addBundle(
                    application.assets.open(pathjoin(PRELOAD_ASSET_PATH, it), AssetManager.ACCESS_STREAMING), run_callbacks = false
                )
            }
        }
    }


    init {
        askForPermissions(
            application, listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
            )
        )
        init_gecko_profiledir()
        // there is no better moment than on bootup (with no browsing sessions open) to perform any outstanding work
        // after that, runs every CACHE_INJECTION_INTERVAL. Normally the event-based callbacks would take care of injections, but if one
        // or more fail they would not be retried until a new trigger event occurs; hence we schedule this janitorial work.
        copyPreloads()
        injectAll(include_serviceworkers = true)
        ServiceworkerRegistry(profiledir).merge_staged()
        koekoeksNest.also {
            it.CollectionUpdateCallbacks["${this::class.java.simpleName}_notify"] = { swUpdateNotification.poke() }
            it.SubscriptionUpdateCallbacks["${this::class.java.simpleName}_notify"] = { swUpdateNotification.poke() }
        }
        eekhoorn = HttpEekhoorn(
            eekhoorn_basedir = application.filesDir,
            portno = if (BuildConfig.DEBUG) EEKHOORN_DEBUG_PORT else 0,
            username_password = if (BuildConfig.DEBUG) EEKHOORN_DEBUG_CREDENTIALS else null,
            appelflapBridge = this,
            pkiOps = koekoeksNest.pkiOps,
            koekoekBridge = koekoeksNest,
            DEBUG = BuildConfig.DEBUG
        ).also {
            ConductorService.startService(application.applicationContext, it.get_portno())
            writeEekhoornInfo(it)
        }
        writePeerIDInfo()
        geckoRuntime = createRuntime()
    }
}
