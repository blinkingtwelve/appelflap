package org.nontrivialpursuit.appelflap.webwrap

import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.util.Base64
import io.sentry.Sentry
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.NavigationDelegate.LoadRequest
import org.mozilla.geckoview.WebRequestError
import org.nontrivialpursuit.appelflap.Logger
import org.nontrivialpursuit.appelflap.R

const val ERRORPAGE_ASSET_LOCATION = "html_templates/errorpage.tmpl.html"

internal class CustomNavigationDelegate(private val geckoWrap: GeckoWrap) : NavigationDelegate {
    val log = Logger(this)
    var currentUri: String? = null
        private set
    var canWeGoBack = false
        private set

    override fun onLocationChange(session: GeckoSession, url: String?, perms: List<GeckoSession.PermissionDelegate.ContentPermission>) {
        currentUri = url
    }

    override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
        canWeGoBack = canGoBack
    }

    override fun onLoadRequest(session: GeckoSession, request: LoadRequest): GeckoResult<AllowOrDeny> {
        log.v(
            "onLoadRequest=${request.uri} triggerUri=${request.triggerUri} where=${request.target} isRedirect=${request.isRedirect}"
        )
        if (request.uri == "about:blank") {
            session.loadUri(geckoWrap.siteUrls.wrapped_site_url)
            return GeckoResult.allow()
        }
        if (geckoWrap.siteUrls.allowlist.contains(request.uri)) {
            return GeckoResult.allow() // may still be rejected, see GeckoRuntimeSettings
        }
        val uri = Uri.parse(request.uri)
        if (geckoWrap.siteUrls.in_our_domains(uri) || uri.scheme == "data") {
            return GeckoResult.allow()
        } else if (geckoWrap.siteUrls.launchable_schemes.contains(uri.scheme)) {
            val bump_external = Intent(Intent.ACTION_VIEW, uri).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            geckoWrap.packageManager.resolveActivity(bump_external, 0)?.also {
                geckoWrap.startActivity(bump_external)
            }
        }
        return GeckoResult.allow()
    }

    override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
        log.v("onNewSession: ${uri}")
        return null
    }

    override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
        val (weberr_cat, weberr_code) = listOf(error.category, error.code).map(::weberr)
        Sentry.captureMessage("Load error: uri:${uri}, error category: ${weberr_cat}, error: ${weberr_code}")
        val errorpage = geckoWrap.assets.open(ERRORPAGE_ASSET_LOCATION).bufferedReader().use {
            it.readText()
        }
        val fmt_args = listOf(
            weberr_cat,
            weberr_code,
            geckoWrap.siteUrls.wrapped_site_url,
            geckoWrap.resources.getString(R.string.errorpage_header),
            geckoWrap.resources.getString(R.string.errorpage_try_again)
        ).map {
            TextUtils.htmlEncode(
                it
            )
        }.toTypedArray()
        val rendered = errorpage.format(*fmt_args)
        return GeckoResult.fromValue("data:text/html;base64,${Base64.encodeToString(rendered.toByteArray(), Base64.NO_WRAP)}")
    }
}