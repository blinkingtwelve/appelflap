package org.nontrivialpursuit.appelflap.webwrap

import android.text.Html
import android.widget.Toast
import io.nayuki.qrcodegen.QrCode
import org.nontrivialpursuit.appelflap.Appelflap
import org.nontrivialpursuit.appelflap.R
import org.nontrivialpursuit.appelflap.getWifiDigest
import org.nontrivialpursuit.eekhoorn.GIFT_PATH
import java.net.URL

const val GIFT_PAGE_TEMPLATE = "html_templates/giftpage.tmpl.html"

fun gen_qr(url: URL): String {
    return QrCode.encodeText(url.toString(), QrCode.Ecc.MEDIUM).toSvgString(4)
}

fun gen_QRpage(gw: GeckoWrap): String? {
    return getWifiDigest(gw).let {
        it.ipaddress?.let { ipaddress ->
            Appelflap.get(gw).geckoRuntimeManager?.eekhoorn?.get_portno()?.let { portno ->
                val my_uri = URL("http", ipaddress, portno, GIFT_PATH)
                val thepage: String = gw.assets.open(GIFT_PAGE_TEMPLATE).reader().use {
                    it.readText()
                }
                thepage.format(Html.escapeHtml(it.ssid?.takeIf { it.startsWith('"') } ?: "?"), my_uri.toString(), gen_qr(my_uri))
            }
        }
    } ?: Toast.makeText(gw, R.string.wifi_not_associated, Toast.LENGTH_SHORT).show().let { null }
}

