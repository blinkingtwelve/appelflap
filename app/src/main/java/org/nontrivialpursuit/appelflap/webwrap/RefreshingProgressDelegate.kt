package org.nontrivialpursuit.appelflap.webwrap

import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ProgressDelegate
import org.mozilla.geckoview.GeckoSession.ScrollDelegate
import org.nontrivialpursuit.appelflap.R
import org.nontrivialpursuit.appelflap.webwrap.CustomSwipeRefreshLayout.CanChildScrollUpCallback

internal class RefreshingProgressDelegate(private val geckoWrap: GeckoWrap) : ProgressDelegate, ScrollDelegate, CanChildScrollUpCallback {
    private val swipelayout: CustomSwipeRefreshLayout = geckoWrap.findViewById(R.id.webwrap_swipelayout)
    var seshstate: GeckoSession.SessionState? = null
    var scrollY = 0

    override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
        seshstate = sessionState
    }

    override fun onPageStart(session: GeckoSession, url: String) {
        scrollY = 0
        swipelayout.isRefreshing = true
    }

    override fun onPageStop(session: GeckoSession, success: Boolean) {
        swipelayout.isRefreshing = false
    }

    override fun canSwipeRefreshChildScrollUp(): Boolean {
        // We abuse this to enable/disable the pulldown that will pop up our ActionBar.
        return scrollY > 10  // 0 would be logical, but results in quirky behaviour if gestures are jerky
    }

    override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
        this.scrollY = scrollY
    }

    fun setRefreshing(on_or_off: Boolean) {
        swipelayout.isRefreshing = on_or_off
    }

    init {
        swipelayout.setCanChildScrollUpCallback(this)
    }
}