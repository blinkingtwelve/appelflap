package org.nontrivialpursuit.appelflap.webwrap

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import org.mozilla.geckoview.*
import org.nontrivialpursuit.appelflap.*
import org.nontrivialpursuit.appelflap.BuildConfig
import org.nontrivialpursuit.appelflap.R
import org.nontrivialpursuit.appelflap.peddlenet.*
import org.nontrivialpursuit.appelflap.webpushpoll.PollScheduler

const val REQUEST_FILE_PICKER = 1
const val INTENT_EXTRA_NOTIFICATION_CLICK = "NOTIFICATION_CLICK"

class GeckoWrap : Activity() {
    private lateinit var geckoViewport: GeckoView
    val log = Logger(this)
    val lobroman = getLocalBroadcastManager(this)
    lateinit var siteUrls: SiteUrls
    lateinit var statusnotifier: StatusNotifier
    lateinit var BACKGROUNDED_MESSAGE: String
    val application: Appelflap
        get() {
            return Appelflap.get(this)
        }

    @JvmField
    var geckoSession: GeckoSession? = null
    lateinit var downloads: Downloads
    private lateinit var progressDelegate: RefreshingProgressDelegate
    private lateinit var navops: CustomNavigationDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_geckowrap)
        actionBar?.apply {
            hide()
        }

        progressDelegate = RefreshingProgressDelegate(this)
        geckoViewport = findViewById(R.id.webwrap_viewport)
        siteUrls = SiteUrls(applicationContext)
        downloads = Downloads(this)
        navops = CustomNavigationDelegate(this)
        statusnotifier = StatusNotifier(this.applicationContext)
        BACKGROUNDED_MESSAGE = resources.getString(R.string.p2p_backgrounded_no_wifihost) + " \uD83D\uDE22"
        (findViewById<CustomSwipeRefreshLayout>(R.id.webwrap_swipelayout)).setOnRefreshListener {
            progressDelegate.setRefreshing(false)
        }
        if (savedInstanceState != null && application.geckoRuntimeManager != null) return
        setupSession()
        loadUrlFromIntent(intent)
        // schedule webpush poller, if appropriate. May or may not have been done already using the boot event receiver but it's idempotent.
        if (BuildConfig.WEBPUSH_POLL_INTERVAL_QTR > 0 && BuildConfig.WEBPUSH_POLL_URL.length > 0) {
            PollScheduler.schedule(application, false)
        }
    }

    fun setupSession() {
        GeckoRuntimeManager.getInstance(application, intent.extras).also { rtm ->
            rtm.wrapper = this
            rtm.geckoRuntime.attachTo(applicationContext)
            instantiateSession(rtm)
        }
    }

    @Synchronized
    fun instantiateSession(rtm: GeckoRuntimeManager, restoreState: GeckoSession.SessionState? = null) {
        this.runOnUiThread {
            geckoSession = geckoSession ?: geckoViewport.session ?: intent.getParcelableExtra("session") ?: createSession()
            geckoSession?.also {
                if (!it.isOpen) it.open(rtm.geckoRuntime)
                setSessionDelegates(it)
                geckoViewport.setSession(it)
                restoreState?.also { state ->
                    it.restoreState(state)
                } ?: goHome()
            }
        }
    }

    fun stopSession(): GeckoSession.SessionState? {
        this.runOnUiThread {
            val we_stopped = this.geckoViewport.releaseSession()?.close()?.let { true } ?: false
            if (we_stopped) this.geckoSession = null
        }
        return progressDelegate.seshstate
    }

    private fun setSessionDelegates(session: GeckoSession) {
        session.contentDelegate = CustomContentDelegate(this)
        session.progressDelegate = progressDelegate
        session.scrollDelegate = progressDelegate
        session.navigationDelegate = navops
        session.promptDelegate = GeckoWrapPrompt(this, REQUEST_FILE_PICKER)
        session.selectionActionDelegate = BasicSelectionActionDelegate(this)
        session.permissionDelegate = AutograntingPermissionDelegate()
    }

    private fun createSession(): GeckoSession {
        val gss = GeckoSessionSettings.Builder().usePrivateMode(false).useTrackingProtection(false)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE).userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE).build()
        return GeckoSession(gss)
    }

    fun goHome() {
        geckoSession?.load(
            GeckoSession.Loader().uri(siteUrls.wrapped_site_url).flags(
                GeckoSession.LOAD_FLAGS_REPLACE_HISTORY
            )
        )
    }

    fun informReboot() {
        runOnUiThread {
            progressDelegate.setRefreshing(true)
            Toast.makeText(this, R.string.gecko_engine_restart, Toast.LENGTH_SHORT).show()
        }
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        geckoViewport.session?.also {
            geckoSession = it
            setSessionDelegates(it)
            it.reload()
        }
    }

    public override fun onDestroy() {
        log.i("onDestroy")
        ConductorService.stopService(this)
        geckoViewport.releaseSession() // avoids what seems like multiple things grabbing the keyboard input, symptom: rapidly flickering cursor and missed keypresses in text field
        geckoSession = null
        super.onDestroy()
    }

    fun notifyBGFG_Conductor(is_backgrounded: Boolean) {
        lobroman.sendBroadcast(Intent(this, Conductor::class.java).also {
            it.setAction(BACKGROUNDED_ACTION)
            it.putExtra(BACKGROUNDED_ACTION, is_backgrounded)
        })
    }

    override fun onResume() {
        log.i("onResume")
        notifyBGFG_Conductor(false)
        statusnotifier.clear()
        super.onResume()
    }

    override fun onPause() {
        log.i("onPause")
        notifyBGFG_Conductor(true)
        statusnotifier.setStatus(BACKGROUNDED_MESSAGE)
        super.onPause()
    }

    override fun onStop() {
        log.i("onStop")
        statusnotifier.setStatus(BACKGROUNDED_MESSAGE)
        super.onStop()
    }


    private fun loadUrlFromIntent(intent: Intent) {
        progressDelegate.setRefreshing(true)
        geckoSession?.loadUri(intent.data?.let { if (siteUrls.in_our_domains(it)) it.toString() else null } ?: siteUrls.wrapped_site_url)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Android notifications, feed back to application what notification was tapped (clicked)
        intent.getStringExtra(INTENT_EXTRA_NOTIFICATION_CLICK)?.also {
            (GeckoRuntimeManager.getInstance(this.application).geckoRuntime.webNotificationDelegate as WebnotificationsHandler).click(it)
        }
        setIntent(intent)
        intent.data?.also { loadUrlFromIntent(intent) }
    }

    override fun onBackPressed() {
        when (navops.canWeGoBack) {
            true -> geckoSession?.goBack()
            else -> super.onBackPressed()
        }
    }

    fun loadHtml(html: String) {
        geckoSession?.load(
            GeckoSession.Loader().data(html.encodeToByteArray(), "text/html").flags(GeckoSession.LOAD_FLAGS_FORCE_ALLOW_DATA_URI)
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FILE_PICKER) {
            geckoSession?.also {
                (it.promptDelegate as GeckoWrapPrompt).onFileCallbackResult(resultCode, data)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}