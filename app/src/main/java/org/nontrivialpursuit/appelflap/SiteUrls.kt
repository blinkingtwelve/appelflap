package org.nontrivialpursuit.appelflap

import android.content.Context
import android.net.Uri
import org.nontrivialpursuit.appelflap.BuildConfig.DEBUG

class SiteUrls(context: Context) {
    @kotlin.jvm.JvmField
    val launchable_schemes = setOf("http", "https")
    var our_schemes: Set<String> = HashSet()
    val debug_allowlistedlisted = setOf("about:config")
    val allowlist = HashSet<String>()

    @kotlin.jvm.JvmField
    var wrapped_site_url: String
    var our_domains: Set<String>

    fun in_our_domains(theurlstring: String): Boolean {
        return in_our_domains(Uri.parse(theurlstring))
    }

    fun in_our_domains(theurl: Uri): Boolean {
        if (our_schemes.contains(theurl.scheme)) {
            for (domain in our_domains) {
                if (theurl.host?.endsWith((domain)) == true) return true
            }
        }
        return false
    }

    init {
        if (DEBUG) {
            our_schemes = setOf("http", "https")
            val prefs = context.getSharedPreferences(DEBUG_SITEURL_PREFS_NAME, Context.MODE_PRIVATE)
            wrapped_site_url = prefs.getString("wrapped_site_url", BuildConfig.WEBWRAP_URL)!!
            our_domains = prefs.getStringSet("our_domains", BuildConfig.WEBWRAP_DOMAINS.toSet())!!
            allowlist.addAll(debug_allowlistedlisted)
        } else {
            our_schemes = setOf("https")
            wrapped_site_url = BuildConfig.WEBWRAP_URL
            our_domains = BuildConfig.WEBWRAP_DOMAINS.toSet()
        }
    }
}