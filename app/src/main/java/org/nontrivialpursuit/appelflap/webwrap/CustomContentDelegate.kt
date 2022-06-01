package org.nontrivialpursuit.appelflap.webwrap

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement
import org.mozilla.geckoview.WebResponse
import org.nontrivialpursuit.appelflap.Appelflap
import org.nontrivialpursuit.appelflap.Logger

internal class CustomContentDelegate(private val geckoWrap: GeckoWrap) : ContentDelegate {

    val log = Logger(this)

    override fun onTitleChange(session: GeckoSession, title: String?) {
        log.v("Content title changed to $title")
    }

    override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
        log.v(String.format("Webpage wants fullscreen: %b", fullScreen))
    }

    override fun onFocusRequest(session: GeckoSession) {
        log.v("Content requesting focus")
    }

    override fun onCloseRequest(session: GeckoSession) {
        log.i("Exiting application due to page context window.close()")
        if (session === geckoWrap.geckoSession) {
            Appelflap.get(geckoWrap).geckoRuntimeManager?.shutdown()
        }
    }

    override fun onContextMenu(
            session: GeckoSession, screenX: Int, screenY: Int, element: ContextElement) {
        log.v(
            "onContextMenu screenX=" + screenX + " screenY=" + screenY + " type=" + element.type + " linkUri=" + element.linkUri + " title=" + element.title + " alt=" + element.altText + " srcUri=" + element.srcUri
        )
    }


    override fun onExternalResponse(session: GeckoSession, response: WebResponse) {

        val forceDownload = response.headers["Content-Disposition"]?.startsWith("attachment") ?: false
        when (forceDownload) {
            true -> geckoWrap.downloads.startDownload(response)
            false -> {
                response.getDownloadDescriptor().mimetype()?.also { mimetype ->
                    try {
                        geckoWrap.startActivity(Intent(Intent.ACTION_VIEW).setDataAndTypeAndNormalize(Uri.parse(response.uri), mimetype))
                    } catch (e: ActivityNotFoundException) {
                        geckoWrap.downloads.startDownload(response)
                    }
                } ?: geckoWrap.downloads.startDownload(response)
            }
        }
    }


    override fun onCrash(session: GeckoSession) {
        log.e("Session crashed, relaunching app")
        Appelflap.get(geckoWrap).relaunchViaTrampoline()
    }
}